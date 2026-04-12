package com.aykhedma.service;

import com.aykhedma.dto.request.ChatMessageRequest;
import com.aykhedma.dto.response.ChatMessageResponse;
import com.aykhedma.model.chat.*;
import com.aykhedma.model.user.User;
import com.aykhedma.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MediaStorageService mediaStorageService;

    public ChatRoom getOrCreateRoom(User sender, Long receiverId) {

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        if (sender.getId().equals(receiver.getId())) {
            throw new RuntimeException("Cannot create chat with yourself");
        }

        Optional<ChatRoom> existingRoom = chatRoomRepository.findRoomBetweenUsers(sender, receiver);

        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        ChatRoom newRoom = ChatRoom.builder()
                .participants(List.of(sender, receiver))
                .isActive(true)
                .roomName(sender.getName() + " & " + receiver.getName())
                .build();

        return chatRoomRepository.save(newRoom);
    }
    @Transactional
    public ChatMessageResponse sendMessage(User sender, ChatMessageRequest request) throws IOException {

        if (sender == null)
            throw new RuntimeException("Sender is null");

        var room = chatRoomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        boolean allowed = room.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(sender.getId()));

        if (!allowed)
            throw new RuntimeException("Unauthorized");


        List<String> mediaUrls = new ArrayList<>();

        if (request.getMediaFiles() != null && !request.getMediaFiles().isEmpty()) {

            for (MultipartFile file : request.getMediaFiles()) {

                if (file != null && !file.isEmpty()) {

                    String contentType = file.getContentType();

                    // optional validation (recommended)
                    if (contentType != null &&
                            !contentType.startsWith("image/") &&
                            !contentType.startsWith("video/")) {
                        throw new RuntimeException("Only image/video files are allowed");
                    }

                    String url = mediaStorageService.storeMedia(file);
                    mediaUrls.add(url);
                }
            }
        }

        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room)
                .senderId(sender.getId())
                .senderRole(MessageRole.USER)
                .content(request.getContent() != null ? request.getContent() : "")
                .mediaUrls(mediaUrls)
                .type(request.getType())
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .build();

        ChatMessage saved = chatMessageRepository.save(msg);

        ChatMessageResponse response =
                ChatMessageResponse.fromEntity(saved, sender.getId(), userRepository);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + room.getId(),
                response
        );

        return response;
    }

    @Transactional
    public List<ChatMessageResponse> getMessages(User currentUser, String roomId, int page, int size) {

        var room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        boolean isParticipant = room.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(currentUser.getId()));

        if (!isParticipant) throw new RuntimeException("Unauthorized");

        chatMessageRepository.markMessagesAsRead(roomId, currentUser.getId());

        var messages = chatMessageRepository.findByChatRoomId(
                roomId,
                PageRequest.of(page, size, Sort.by("timestamp").ascending())
        );

        return messages.stream()
                .map(msg -> ChatMessageResponse.fromEntity(msg, currentUser.getId(), userRepository))
                .toList();
    }
    public long getUnreadCount(String roomId, Long userId) {
        return chatMessageRepository.countUnreadMessages(roomId, userId);
    }


    public void deleteMessage(String id, User user) {
        ChatMessage msg = chatMessageRepository.findById(id).orElseThrow();
        if (!msg.getSenderId().equals(user.getId()))
            throw new RuntimeException("Cannot delete");
        chatMessageRepository.delete(msg);
    }
}