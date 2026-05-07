package com.aykhedma.service.verification;

import com.aykhedma.dto.response.verification.FaceMatchResponse;
import com.aykhedma.dto.response.verification.NidExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface VerificationService {
    NidExtractionResponse extractNid(MultipartFile idImage);
    FaceMatchResponse matchFaces(MultipartFile idImage, MultipartFile selfieImage);
}
