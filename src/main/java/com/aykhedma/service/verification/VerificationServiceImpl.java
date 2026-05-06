package com.aykhedma.service.verification;

import com.aykhedma.dto.response.verification.FaceMatchResponse;
import com.aykhedma.dto.response.verification.NidExtractionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private final WebClient.Builder webClientBuilder;

    @Value("${verification.service.url:http://127.0.0.1:8000}")
    private String verificationServiceUrl;

    @Override
    public NidExtractionResponse extractNid(MultipartFile idImage) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(verificationServiceUrl).build();

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", idImage.getResource())
                   .filename(idImage.getOriginalFilename());

            MultiValueMap<String, HttpEntity<?>> multipartBody = builder.build();

            return webClient.post()
                    .uri("/extract-nid")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipartBody))
                    .retrieve()
                    .bodyToMono(NidExtractionResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Error calling OCR service: {}", e.getMessage());
            return NidExtractionResponse.builder()
                    .valid(false)
                    .error("Verification service unavailable: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public FaceMatchResponse matchFaces(MultipartFile idImage, MultipartFile selfieImage) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(verificationServiceUrl).build();

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("id_image", idImage.getResource())
                   .filename(idImage.getOriginalFilename());
            builder.part("selfie", selfieImage.getResource())
                   .filename(selfieImage.getOriginalFilename());

            MultiValueMap<String, HttpEntity<?>> multipartBody = builder.build();

            return webClient.post()
                    .uri("/face-match")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipartBody))
                    .retrieve()
                    .bodyToMono(FaceMatchResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Error calling Face Match service: {}", e.getMessage());
            return FaceMatchResponse.builder()
                    .match(false)
                    .error("Verification service unavailable: " + e.getMessage())
                    .build();
        }
    }
}
