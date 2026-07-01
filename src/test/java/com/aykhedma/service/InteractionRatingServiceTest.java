package com.aykhedma.service;

import com.aykhedma.dto.request.InteractionRatingRequest;
import com.aykhedma.dto.response.InteractionRatingResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.InteractionRatingMapper;
import com.aykhedma.model.rating.InteractionRating;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.ConsumerRepository;
import com.aykhedma.repository.InteractionRatingRepository;
import com.aykhedma.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InteractionRatingServiceImpl Tests")
class InteractionRatingServiceTest
{
    @Mock
    private InteractionRatingRepository interactionRatingRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private ConsumerRepository consumerRepository;
    @Mock
    private InteractionRatingMapper interactionRatingMapper;
    @Mock
    private NotificationFactory notificationFactory;

    @InjectMocks
    private InteractionRatingServiceImpl interactionRatingService;

    @Captor
    private ArgumentCaptor<InteractionRating> ratingCaptor;

    private Consumer consumer;
    private Provider provider;

    @BeforeEach
    void setUp()
    {
        provider = Provider.builder()
                .id(1L)
                .name("Provider One")
                .role(UserType.PROVIDER)
                .averageInteractionRating(0.0)
                .interactionRatingCount(0)
                .build();

        consumer = Consumer.builder()
                .id(2L)
                .name("Consumer One")
                .role(UserType.CONSUMER)
                .build();
    }

    // ========================================================================
    // Submit Interaction Rating
    // ========================================================================
    @Nested
    @DisplayName("Submit Interaction Rating Tests")
    class SubmitInteractionRatingTest
    {
        private InteractionRatingRequest request;

        @BeforeEach
        void setUp()
        {
            request = InteractionRatingRequest.builder()
                    .providerId(provider.getId())
                    .rating(4)
                    .comment("Very professional profile!")
                    .build();
        }

        @Test
        @DisplayName("Successfully Submit Interaction Rating")
        void submitRatingSuccessTest()
        {
            InteractionRating savedRating = InteractionRating.builder()
                    .id(1L)
                    .consumer(consumer)
                    .provider(provider)
                    .rating(4.0)
                    .comment("Very professional profile!")
                    .createdAt(LocalDateTime.now())
                    .build();

            InteractionRatingResponse expectedResponse = InteractionRatingResponse.builder()
                    .id(1L)
                    .consumerId(consumer.getId())
                    .consumerName(consumer.getName())
                    .rating(4.0)
                    .comment("Very professional profile!")
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(interactionRatingRepository.save(any(InteractionRating.class))).thenReturn(savedRating);
            when(interactionRatingMapper.toResponse(savedRating)).thenReturn(expectedResponse);

            InteractionRatingResponse response = interactionRatingService.submitRating(consumer.getId(), request);

            assertThat(response).isNotNull();
            assertThat(response.getRating()).isEqualTo(4.0);
            assertThat(response.getComment()).isEqualTo("Very professional profile!");
            assertThat(response.getConsumerId()).isEqualTo(consumer.getId());
        }

        @Test
        @DisplayName("Correctly Build InteractionRating Entity From Request")
        void submitRatingBuildEntityTest()
        {
            InteractionRating savedRating = InteractionRating.builder()
                    .id(1L).consumer(consumer).provider(provider).rating(4.0).build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(interactionRatingRepository.save(ratingCaptor.capture())).thenReturn(savedRating);
            when(interactionRatingMapper.toResponse(any())).thenReturn(InteractionRatingResponse.builder().build());

            interactionRatingService.submitRating(consumer.getId(), request);

            InteractionRating captured = ratingCaptor.getValue();
            assertThat(captured.getConsumer()).isEqualTo(consumer);
            assertThat(captured.getProvider()).isEqualTo(provider);
            assertThat(captured.getRating()).isEqualTo(4.0);
            assertThat(captured.getComment()).isEqualTo("Very professional profile!");
        }

        @Test
        @DisplayName("Update Provider Interaction Metrics For First Rating")
        void submitRatingFirstMetricUpdateTest()
        {
            InteractionRating savedRating = InteractionRating.builder()
                    .id(1L).consumer(consumer).provider(provider).rating(5.0).build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(interactionRatingRepository.save(any())).thenReturn(savedRating);
            when(interactionRatingMapper.toResponse(any())).thenReturn(InteractionRatingResponse.builder().build());

            request.setRating(5);
            interactionRatingService.submitRating(consumer.getId(), request);

            // First rating: count = 0 → 1, avg = (0*0 + 5) / 1 = 5.0
            assertThat(provider.getInteractionRatingCount()).isEqualTo(1);
            assertThat(provider.getAverageInteractionRating()).isEqualTo(5.0);
            verify(providerRepository).save(provider);
        }

        @Test
        @DisplayName("Update Provider Interaction Metrics With Rolling Average")
        void submitRatingRollingAverageTest()
        {
            provider.setInteractionRatingCount(2);
            provider.setAverageInteractionRating(4.0);

            InteractionRating savedRating = InteractionRating.builder()
                    .id(3L).consumer(consumer).provider(provider).rating(1.0).build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(interactionRatingRepository.save(any())).thenReturn(savedRating);
            when(interactionRatingMapper.toResponse(any())).thenReturn(InteractionRatingResponse.builder().build());

            request.setRating(1);
            interactionRatingService.submitRating(consumer.getId(), request);

            // newCount = 3, newAvg = (4.0*2 + 1.0)/3 = 9.0/3 = 3.0
            assertThat(provider.getInteractionRatingCount()).isEqualTo(3);
            assertThat(provider.getAverageInteractionRating()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("Send Notification To Provider After Interaction Rating")
        void submitRatingSendsNotificationTest()
        {
            InteractionRating savedRating = InteractionRating.builder()
                    .id(1L).consumer(consumer).provider(provider).rating(4.0).build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(interactionRatingRepository.save(any())).thenReturn(savedRating);
            when(interactionRatingMapper.toResponse(any())).thenReturn(InteractionRatingResponse.builder().build());

            interactionRatingService.submitRating(consumer.getId(), request);

            verify(notificationFactory).send(eq(provider.getId()), any(), any());
        }

        @Test
        @DisplayName("Notification Failure Does Not Fail Rating Submission")
        void submitRatingNotificationFailureTest()
        {
            InteractionRating savedRating = InteractionRating.builder()
                    .id(1L).consumer(consumer).provider(provider).rating(4.0).build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(interactionRatingRepository.save(any())).thenReturn(savedRating);
            when(interactionRatingMapper.toResponse(any())).thenReturn(InteractionRatingResponse.builder().build());
            doThrow(new RuntimeException("Push failed")).when(notificationFactory).send(any(), any(), any());

            InteractionRatingResponse response = interactionRatingService.submitRating(consumer.getId(), request);

            assertThat(response).isNotNull();
            // Metrics should still be updated despite notification failure
            assertThat(provider.getInteractionRatingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When Consumer Not Found")
        void submitRatingConsumerNotFoundTest()
        {
            when(consumerRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interactionRatingService.submitRating(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found");
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When Provider Not Found")
        void submitRatingProviderNotFoundTest()
        {
            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interactionRatingService.submitRating(consumer.getId(), request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }

        @Test
        @DisplayName("Submit Rating With Null Comment Succeeds")
        void submitRatingNullCommentTest()
        {
            request.setComment(null);
            InteractionRating savedRating = InteractionRating.builder()
                    .id(1L).consumer(consumer).provider(provider).rating(4.0).comment(null).build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(interactionRatingRepository.save(ratingCaptor.capture())).thenReturn(savedRating);
            when(interactionRatingMapper.toResponse(any())).thenReturn(InteractionRatingResponse.builder().build());

            interactionRatingService.submitRating(consumer.getId(), request);

            assertThat(ratingCaptor.getValue().getComment()).isNull();
        }

        @Test
        @DisplayName("Rounding To 1 Decimal Place For Average Interaction Rating")
        void submitRatingRoundingTest()
        {
            provider.setInteractionRatingCount(2);
            provider.setAverageInteractionRating(3.0);

            InteractionRating savedRating = InteractionRating.builder()
                    .id(3L).consumer(consumer).provider(provider).rating(5.0).build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(interactionRatingRepository.save(any())).thenReturn(savedRating);
            when(interactionRatingMapper.toResponse(any())).thenReturn(InteractionRatingResponse.builder().build());

            request.setRating(5);
            interactionRatingService.submitRating(consumer.getId(), request);

            // newAvg = (3.0*2 + 5.0)/3 = 11.0/3 = 3.6666... → rounded to 3.7
            assertThat(provider.getAverageInteractionRating()).isEqualTo(3.7);
        }
    }

    // ========================================================================
    // Get Provider Interaction Ratings
    // ========================================================================
    @Nested
    @DisplayName("Get Provider Interaction Ratings Tests")
    class GetProviderRatingsTest
    {
        @Test
        @DisplayName("Successfully Get Provider Ratings")
        void getProviderRatingsSuccessTest()
        {
            InteractionRating rating1 = InteractionRating.builder()
                    .id(1L).consumer(consumer).provider(provider).rating(4.0).comment("Good").build();
            InteractionRating rating2 = InteractionRating.builder()
                    .id(2L).consumer(consumer).provider(provider).rating(5.0).comment("Excellent").build();

            InteractionRatingResponse resp1 = InteractionRatingResponse.builder()
                    .id(1L).rating(4.0).comment("Good").build();
            InteractionRatingResponse resp2 = InteractionRatingResponse.builder()
                    .id(2L).rating(5.0).comment("Excellent").build();

            when(providerRepository.existsById(provider.getId())).thenReturn(true);
            when(interactionRatingRepository.findByProviderId(provider.getId())).thenReturn(List.of(rating1, rating2));
            when(interactionRatingMapper.toResponseList(List.of(rating1, rating2))).thenReturn(List.of(resp1, resp2));

            List<InteractionRatingResponse> result = interactionRatingService.getProviderRatings(provider.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRating()).isEqualTo(4.0);
            assertThat(result.get(1).getRating()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("Return Empty List When Provider Has No Ratings")
        void getProviderRatingsEmptyTest()
        {
            when(providerRepository.existsById(provider.getId())).thenReturn(true);
            when(interactionRatingRepository.findByProviderId(provider.getId())).thenReturn(List.of());
            when(interactionRatingMapper.toResponseList(List.of())).thenReturn(List.of());

            List<InteractionRatingResponse> result = interactionRatingService.getProviderRatings(provider.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When Provider Not Found")
        void getProviderRatingsProviderNotFoundTest()
        {
            when(providerRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> interactionRatingService.getProviderRatings(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }
    }
}
