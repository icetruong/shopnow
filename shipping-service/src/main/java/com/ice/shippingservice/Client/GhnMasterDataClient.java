package com.ice.shippingservice.Client;

import com.ice.shippingservice.Carrier.Support.CarrierCallExecutor;
import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnDistrict;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnEnvelope;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnProvince;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnWard;
import com.ice.shippingservice.Exception.CarrierApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

/**
 * Đọc master-data địa giới của GHN để seed bảng location_mappings (bước 7).
 * Chỉ dùng khi có GHN_TOKEN (không phụ thuộc carrier.mode - seed là thao tác admin chủ động).
 *
 * <p>VERIFY: path & casing field (PascalCase) đối chiếu tài liệu GHN master-data khi có token.
 */
@Component
public class GhnMasterDataClient {

    private static final String BREAKER = "GHN";
    private static final int GHN_OK = 200;

    private final RestClient http;
    private final CarrierCallExecutor call;
    private final CarrierProperties.Ghn cfg;

    public GhnMasterDataClient(RestClient ghnRestClient, CarrierCallExecutor call, CarrierProperties props) {
        this.http = ghnRestClient;
        this.call = call;
        this.cfg = props.getGhn();
    }

    public List<GhnProvince> provinces() {
        return list("/master-data/province", null,
                new ParameterizedTypeReference<GhnEnvelope<List<GhnProvince>>>() { }, "provinces");
    }

    public List<GhnDistrict> districts(int provinceId) {
        return call.call(BREAKER, "districts", () -> unwrap(
                http.get().uri(u -> u.path("/master-data/district").queryParam("province_id", provinceId).build())
                        .headers(this::auth)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnMasterDataClient::raiseHttp)
                        .body(new ParameterizedTypeReference<GhnEnvelope<List<GhnDistrict>>>() { }),
                "districts"));
    }

    public List<GhnWard> wards(int districtId) {
        return call.call(BREAKER, "wards", () -> unwrap(
                http.get().uri(u -> u.path("/master-data/ward").queryParam("district_id", districtId).build())
                        .headers(this::auth)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnMasterDataClient::raiseHttp)
                        .body(new ParameterizedTypeReference<GhnEnvelope<List<GhnWard>>>() { }),
                "wards"));
    }

    private <T> List<T> list(String path, Object ignored,
                             ParameterizedTypeReference<GhnEnvelope<List<T>>> type, String op) {
        return call.call(BREAKER, op, () -> unwrap(
                http.get().uri(path)
                        .headers(this::auth)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnMasterDataClient::raiseHttp)
                        .body(type),
                op));
    }

    private void auth(HttpHeaders h) {
        h.set("Token", cfg.getToken());
        if (cfg.getShopId() != null && !cfg.getShopId().isBlank()) {
            h.set("ShopId", cfg.getShopId());
        }
    }

    private <T> List<T> unwrap(GhnEnvelope<List<T>> env, String op) {
        if (env == null) {
            throw new CarrierApiException("GHN master-data " + op + " trả body rỗng");
        }
        if (env.code() != GHN_OK) {
            throw new CarrierApiException(
                    "GHN master-data " + op + " lỗi code=" + env.code() + " msg=" + env.message());
        }
        return env.data() != null ? env.data() : List.of();
    }

    private static void raiseHttp(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw new CarrierApiException("GHN master-data trả HTTP lỗi " + response.getStatusCode());
    }
}
