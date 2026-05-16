package com.ice.productservice.Service;

import com.ice.productservice.DTO.Request.Image.ImageRequest;
import com.ice.productservice.DTO.Request.Image.OrderImageRequest;
import com.ice.productservice.DTO.Response.Image.ImageUploadResponse;
import com.ice.productservice.Entity.Product;
import com.ice.productservice.Entity.ProductImage;
import com.ice.productservice.Exception.InvalidFileTypeException;
import com.ice.productservice.Exception.ResourceNotFoundException;
import com.ice.productservice.Exception.TooManyImagesException;
import com.ice.productservice.Repository.ProductImageRepo;
import com.ice.productservice.Repository.ProductRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductImageService {
    private final ProductRepo productRepo;
    private final ProductImageRepo productImageRepo;
    private final MinioService minioService;

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_IMAGES = 10;

    @Transactional
    public List<ImageUploadResponse> uploadImage(UUID productId, List<MultipartFile> files, List<String> altTexts)
    {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("product not found"));

        int currentCount = productImageRepo.countByProduct_Id(productId);
        if(currentCount + files.size() > MAX_IMAGES)
            throw new TooManyImagesException("product cannot have more than " + MAX_IMAGES + " images");

        for(MultipartFile file : files)
        {
            if(!ALLOWED_TYPES.contains(file.getContentType()))
                throw new InvalidFileTypeException("only JPG, PNG, WEBP are allowed");
            if(file.getSize() > MAX_SIZE)
                throw new InvalidFileTypeException("file size must not exceed 5MB");
        }

        List<ImageUploadResponse> responses = new ArrayList<>();
        boolean hasPrimary = productImageRepo.existsByProduct_IdAndIsPrimaryTrue(productId);

        for(int i = 0; i< files.size(); i++)
        {
            MultipartFile file = files.get(i);
            String ext = switch (file.getContentType()) {
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                default -> "jpg";
            };
            String storageKey = "products/" + productId + "/" + UUID.randomUUID() + "." + ext;

            String url;
            try {
                url = minioService.upload(file, storageKey);
            } catch (Exception e) {
                throw new RuntimeException("failed to upload image");
            }

            String altText = (altTexts != null && i < altTexts.size()) ? altTexts.get(i) : null;
            boolean isPrimary = !hasPrimary && i == 0;

            ProductImage image = ProductImage.builder()
                    .product(product)
                    .url(url)
                    .storageKey(storageKey)
                    .altText(altText)
                    .sortOrder(currentCount + i)
                    .isPrimary(isPrimary)
                    .build();

            ProductImage saved = productImageRepo.save(image);
            responses.add(new ImageUploadResponse(
                    saved.getId().toString(),
                    saved.getUrl(),
                    saved.getAltText(),
                    saved.getSortOrder(),
                    saved.getIsPrimary()
            ));
        }

        return responses;
    }

    @Transactional
    public void updatePrimary(UUID productId, UUID imageId)
    {
        ProductImage currentPrimary = productImageRepo.findByProduct_IdAndIsPrimaryTrue(productId)
                .orElse(null);
        ProductImage productImage = productImageRepo.findByIdAndProduct_Id(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("not found Image"));
        if(currentPrimary != null)
        {
            currentPrimary.setIsPrimary(false);
            productImageRepo.save(currentPrimary);
        }

        productImage.setIsPrimary(true);
        productImageRepo.save(productImage);
    }

    @Transactional
    public void updateSort(UUID productId,OrderImageRequest request)
    {
        List<ProductImage> productImages = productImageRepo.findAllByProduct_Id(productId);

        Map<String, Integer> map = new HashMap<>();
        for(ImageRequest imageRequest: request.getOrders())
        {
            map.put(imageRequest.getImageId(), imageRequest.getSortOrder());
        }

        for(ProductImage productImage : productImages)
        {
            if(map.containsKey(productImage.getId().toString()))
                productImage.setSortOrder(map.get(productImage.getId().toString()));
        }

        productImageRepo.saveAll(productImages);
    }

    @Transactional
    public void deleteImage(UUID productId, UUID imageId)
    {
        ProductImage productImage = productImageRepo.findByIdAndProduct_Id(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("not found Image"));

        productImageRepo.delete(productImage);

        try
        {
            minioService.delete(productImage.getStorageKey());
        }
        catch (Exception e)
        {
            throw new RuntimeException("failed to delete image");
        }
    }
}
