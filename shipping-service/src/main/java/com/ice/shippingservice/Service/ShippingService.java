package com.ice.shippingservice.Service;

import com.ice.shippingservice.Carrier.CarrierClient;
import com.ice.shippingservice.Carrier.CarrierClientFactory;
import com.ice.shippingservice.Config.ShippingProperties;
import com.ice.shippingservice.DTO.Carrier.FeeQuote;
import com.ice.shippingservice.DTO.Carrier.FeeRequest;
import com.ice.shippingservice.DTO.Request.ShippingFeeRequest;
import com.ice.shippingservice.DTO.Response.Shipping.ShippingFeeResponse;
import com.ice.shippingservice.Exception.FeeCalculationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingService {
    private static final long CACHE_TTL_SECONDS = 3600;

    private final RedisTemplate<String, Object> redisTemplate;
    private final CarrierClientFactory carrierClientFactory;
    private final ShippingProperties shippingProperties;

    public List<ShippingFeeResponse> calculateFee(ShippingFeeRequest request) {
        ShippingProperties.DefaultPackage pkg = shippingProperties.getDefaultPackage();
        int length = request.getLength() != null ? request.getLength() : pkg.getLength();
        int width  = request.getWidth()  != null ? request.getWidth()  : pkg.getWidth();
        int height = request.getHeight() != null ? request.getHeight() : pkg.getHeight();
        long insurance = request.getInsuranceValue() != null ? request.getInsuranceValue() : 0L;

        var from = shippingProperties.getFrom();
        FeeRequest feeRequest = new FeeRequest(
                from.getDistrictId(),
                from.getWardCode(),
                request.getToDistrictId(),
                request.getToWardCode(),
                null,
                null,
                null,
                request.getWeight(),
                length,
                width,
                height,
                insurance
        );

        String cacheKey = buildCacheKey(feeRequest);

        Object cache = redisTemplate.opsForValue().get(cacheKey);

        if(cache != null)
            return (List<ShippingFeeResponse>) cache;

        List<FeeQuote> feeQuotes = new ArrayList<>();
        for(CarrierClient client : carrierClientFactory.forFeeQuote())
        {
            try
            {
                feeQuotes.addAll(client.calculateFee(feeRequest));
            }
            catch (Exception e)
            {

            }
        }

        if (feeQuotes.isEmpty()) {
            throw new FeeCalculationException("Không tính được phí ship từ bất kỳ nhà vận chuyển nào.");
        }

        List<ShippingFeeResponse> responses = new ArrayList<>(
                feeQuotes.stream().map(feeQuote -> new ShippingFeeResponse(
                        feeQuote.carrier().name(),
                        feeQuote.serviceId(),
                        feeQuote.serviceName(),
                        feeQuote.fee(),
                        feeQuote.estimatedDays(),
                        feeQuote.estimatedDate()
                )).toList()
        );

        redisTemplate.opsForValue().set(cacheKey, responses, Duration.ofSeconds(CACHE_TTL_SECONDS));

        return responses;
    }

    private String buildCacheKey(FeeRequest r) {
        return "shipping:fee:" + r.fromDistrictId()
                + ":" + r.toDistrictId()
                + ":" + r.toWardCode()
                + ":" + r.weightGram();
    }
}
