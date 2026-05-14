package com.qlda.userservice.Service;

import com.qlda.userservice.Exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.base-url}")
    private String baseUrl;

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    public String saveAvatar(MultipartFile file)
    {
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new AppException("Đuôi ảnh không hợp lệ");

        // Validate size
        if (file.getSize() > MAX_SIZE)
            throw new AppException("Size ảnh quá lớn");

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains("."))
            throw new AppException("File không hợp lệ");
        String extension = getExtension(originalFilename);


        String filename = UUID.randomUUID() + "." + extension;

        // Tạo thư mục nếu chưa có
        Path uploadPath = Paths.get(uploadDir);
        Path filePath = uploadPath.resolve(filename);
        try {
            if (!Files.exists(uploadPath))
                Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), filePath);
        } catch (IOException e) {
            throw new AppException("Lỗi khi lưu file: " + e.getMessage());
        }


        // Trả về URL
        return baseUrl + "/uploads/" + filename;

    }

    private String getExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

}
