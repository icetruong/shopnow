package com.ice.shippingservice.Service;

import com.ice.shippingservice.Client.GhnMasterDataClient;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnDistrict;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnProvince;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnWard;
import com.ice.shippingservice.Entity.LocationMapping;
import com.ice.shippingservice.Repository.LocationMappingRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationMappingSeedServiceTest {

    @Mock
    GhnMasterDataClient masterData;
    @Mock
    LocationMappingRepo repo;
    @InjectMocks
    LocationMappingSeedService service;

    @Test
    void sync_insertsNewRowsWithNormalizedNames() {
        when(masterData.provinces()).thenReturn(List.of(new GhnProvince(202, "TP. Hồ Chí Minh")));
        when(masterData.districts(202)).thenReturn(List.of(new GhnDistrict(1442, "Quận 1", 202)));
        when(masterData.wards(1442)).thenReturn(List.of(new GhnWard("21012", "Phường Bến Nghé", 1442)));
        when(repo.findByProvinceNameAndDistrictNameAndWardName(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        LocationMappingSeedService.SeedResult r = service.sync();

        assertThat(r.inserted()).isEqualTo(1);
        assertThat(r.updated()).isZero();
        assertThat(r.wards()).isEqualTo(1);

        ArgumentCaptor<List<LocationMapping>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(repo).saveAll(captor.capture());
        LocationMapping saved = captor.getValue().get(0);
        assertThat(saved.getGhnDistrictId()).isEqualTo(1442);
        assertThat(saved.getGhnWardCode()).isEqualTo("21012");
        assertThat(saved.getProvinceNameNormalized()).isEqualTo("ho chi minh");
        assertThat(saved.getDistrictNameNormalized()).isEqualTo("1");
        assertThat(saved.getWardNameNormalized()).isEqualTo("ben nghe");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void sync_updatesExistingRowInsteadOfInserting() {
        when(masterData.provinces()).thenReturn(List.of(new GhnProvince(202, "TP. Hồ Chí Minh")));
        when(masterData.districts(202)).thenReturn(List.of(new GhnDistrict(1442, "Quận 1", 202)));
        when(masterData.wards(1442)).thenReturn(List.of(new GhnWard("21012", "Phường Bến Nghé", 1442)));
        LocationMapping existing = LocationMapping.builder()
                .provinceName("TP. Hồ Chí Minh").districtName("Quận 1").wardName("Phường Bến Nghé")
                .provinceNameNormalized("x").districtNameNormalized("x").wardNameNormalized("x")
                .ghnProvinceId(1).ghnDistrictId(1).ghnWardCode("0")
                .build();
        when(repo.findByProvinceNameAndDistrictNameAndWardName(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existing));

        LocationMappingSeedService.SeedResult r = service.sync();

        assertThat(r.inserted()).isZero();
        assertThat(r.updated()).isEqualTo(1);
        assertThat(existing.getGhnDistrictId()).isEqualTo(1442);
        assertThat(existing.getWardNameNormalized()).isEqualTo("ben nghe");
    }

    @Test
    void sync_skipsWardsWithBlankNameOrCode() {
        when(masterData.provinces()).thenReturn(List.of(new GhnProvince(202, "TP. HCM")));
        when(masterData.districts(202)).thenReturn(List.of(new GhnDistrict(1442, "Quận 1", 202)));
        when(masterData.wards(anyInt())).thenReturn(List.of(
                new GhnWard("", "Phường X", 1442),
                new GhnWard("21012", "  ", 1442)));

        LocationMappingSeedService.SeedResult r = service.sync();

        assertThat(r.wards()).isZero();
        assertThat(r.inserted()).isZero();
        org.mockito.Mockito.verify(repo).saveAll(any());
    }
}
