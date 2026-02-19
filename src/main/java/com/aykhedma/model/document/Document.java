package com.aykhedma.model.document;

import com.aykhedma.model.user.Provider;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Document title is required")
    @Size(min = 2, max = 100, message = "Title must be between 2 and 100 characters")
    @Column(nullable = false, length = 100)
    private String title;

    @NotBlank(message = "Document type is required")
    @Pattern(regexp = "^(NATIONAL_ID|CERTIFICATE|LICENSE|PROFILE_IMAGE|PORTFOLIO|OTHER)$",
            message = "Invalid document type")
    @Column(nullable = false, length = 50)
    private String type;

    @NotBlank(message = "File path is required")
    @Size(max = 500, message = "File path cannot exceed 500 characters")
    @Column(nullable = false, length = 500)
    private String filePath;

    @PastOrPresent(message = "Updated date cannot be in the future")
    @UpdateTimestamp
    private LocalDateTime updatedDate;

    @PastOrPresent(message = "Updated date cannot be in the future")
    @UpdateTimestamp
    private LocalDateTime uploadedDate;


    @NotNull(message = "Provider is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;
}