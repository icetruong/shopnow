package com.ice.shippingservice.Carrier;

import com.ice.shippingservice.Enum.CarrierType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CarrierClientFactory {

    private static final String MODE_MOCK = "mock";

    private final String mode;                              // carrier.mode
    private final MockCarrierClient mockClient;
    private final Map<CarrierType, CarrierClient> realClients;

    public CarrierClientFactory(
            @Value("${carrier.mode:mock}") String mode,
            MockCarrierClient mockClient,
            List<CarrierClient> allClients) {               // Spring inject TẤT CẢ bean CarrierClient
        this.mode = mode == null ? MODE_MOCK : mode.trim();
        this.mockClient = mockClient;
        this.realClients = allClients.stream()
                .filter(c -> c.carrierType() != null)       // loại MockCarrierClient (carrierType()==null)
                .collect(Collectors.toMap(CarrierClient::carrierType, Function.identity()));
    }

    public CarrierClient forCarrier(CarrierType type) {
        if (MODE_MOCK.equalsIgnoreCase(mode)) {
            return mockClient;                              // mock: mọi carrier đều về đây
        }
        CarrierClient client = realClients.get(type);
        if (client == null) {
            throw new IllegalStateException(
                    "Chưa có CarrierClient cho " + type + " khi carrier.mode=real");
        }
        return client;
    }
}
