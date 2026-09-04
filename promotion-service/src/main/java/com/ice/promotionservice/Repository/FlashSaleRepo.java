package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.FlashSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FlashSaleRepo extends JpaRepository<FlashSale, UUID> {

    @Query("""
          SELECT fs FROM FlashSale fs
          WHERE fs.startsAt <= :now AND fs.endsAt >= :now
          ORDER BY fs.endsAt ASC
""")
    List<FlashSale> findActive(@Param("now") LocalDateTime now);
}
