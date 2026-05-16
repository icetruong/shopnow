package com.ice.productservice.Service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MinioService {
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.url}")
    private String minioUrl;

    public String upload(MultipartFile file, String storageKey) throws Exception
    {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(storageKey)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        return minioUrl + "/" + bucket + "/" + storageKey;
    }

    public void delete(String storageKey) throws Exception
    {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(storageKey)
                        .build()
        );
    }

}
