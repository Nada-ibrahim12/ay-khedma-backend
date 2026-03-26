package com.aykhedma.service;

import com.aykhedma.dto.request.ChatMessageRequest;
import com.aykhedma.dto.response.ChatMessageResponse;
import com.aykhedma.dto.response.DraftMessageResponse;
import com.aykhedma.model.chat.*;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.ChatMessageRepository;
import com.aykhedma.repository.ChatRoomRepository;
import com.aykhedma.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MediaStorageService mediaStorageService;

    // Key = roomId:userId
    private final Map<String, String> draftMessages = new ConcurrentHashMap<>();

    private String draftKey(String roomId, Long userId) {
        return roomId + ":" + userId;
    }

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
        ChatRoom room = chatRoomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        boolean allowed = room.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(sender.getId()));
        if (!allowed) throw new RuntimeException("Unauthorized");

        MessageRole role = MessageRole.USER;

        String mediaUrl = null;
        MultipartFile mediaFile = request.getMediaFile();
        if (mediaFile != null && !mediaFile.isEmpty()) {
            mediaUrl = mediaStorageService.storeMedia(mediaFile);
        }
        String content = request.getContent();
        if (content == null) content = "";

        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room)
                .senderId(sender.getId())
                .senderRole(role)
                .content(content)
                .mediaUrl(mediaUrl)
                .type(request.getType() != null ? request.getType() : MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .build();

        ChatMessage saved = chatMessageRepository.save(msg);

        room.setLastMessageAt(saved.getTimestamp());
        chatRoomRepository.save(room);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + room.getId(),
                ChatMessageResponse.fromEntity(saved)
        );
        return ChatMessageResponse.fromEntity(saved);
    }

    @Transactional
    public List<ChatMessageResponse> getMessages(User currentUser, String roomId, int page, int size) {

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        boolean isParticipant = room.getParticipants()
                .stream()
                .anyMatch(u -> u.getId().equals(currentUser.getId()));
        if (!isParticipant) throw new RuntimeException("Unauthorized");

        chatMessageRepository.markMessagesAsRead(roomId, currentUser.getId());

        var messagesPage = chatMessageRepository.findByChatRoomId(
                roomId,
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("timestamp").ascending())
        );

        return messagesPage.stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }

    public long getUnreadCount(String roomId, Long userId) {
        return chatMessageRepository.countUnreadMessages(roomId, userId);
    }

    public ChatMessageResponse editMessage(String id, String content, User user) {
        ChatMessage msg = chatMessageRepository.findById(id).orElseThrow();
        if (!msg.getSenderId().equals(user.getId()))
            throw new RuntimeException("Cannot edit");
        msg.setContent(content);
        return ChatMessageResponse.fromEntity(chatMessageRepository.save(msg));
    }

    public void deleteMessage(String id, User user) {
        ChatMessage msg = chatMessageRepository.findById(id).orElseThrow();
        if (!msg.getSenderId().equals(user.getId()))
            throw new RuntimeException("Cannot delete");
        chatMessageRepository.delete(msg);
    }

    // ====================== Draft Message Logic ======================

    public DraftMessageResponse getOrCreateDraft(User sender, Long receiverId) {
        ChatRoom room = getOrCreateRoom(sender, receiverId);
        String defaultMessage = "Hi, unfortunately your booking cannot be accepted. Please reschedule.";

        String key = draftKey(room.getId(), sender.getId());
        String draft = draftMessages.getOrDefault(key, defaultMessage);

        return DraftMessageResponse.builder()
                .roomId(room.getId())
                .draftMessage(draft)
                .build();
    }

    public DraftMessageResponse updateDraft(String roomId, Long userId, String newContent) {
        String key = draftKey(roomId, userId);
        draftMessages.put(key, newContent);

        return DraftMessageResponse.builder()
                .roomId(roomId)
                .draftMessage(newContent)
                .build();
    }

    @Transactional
    public ChatMessageResponse sendDraft(String roomId, User sender) {
        String key = draftKey(roomId, sender.getId());
        String content = draftMessages.get(key);

        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Draft message is empty");
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room)
                .senderId(sender.getId())
                .senderRole(MessageRole.USER)
                .content(content)
                .type(MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .build();

        ChatMessage saved = chatMessageRepository.save(msg);

        // Update last message timestamp
        room.setLastMessageAt(saved.getTimestamp());
        chatRoomRepository.save(room);

        // Broadcast
        messagingTemplate.convertAndSend(
                "/topic/chat/" + room.getId(),
                ChatMessageResponse.fromEntity(saved)
        );

        // Remove draft after sending
        draftMessages.remove(key);

        return ChatMessageResponse.fromEntity(saved);
    }
}