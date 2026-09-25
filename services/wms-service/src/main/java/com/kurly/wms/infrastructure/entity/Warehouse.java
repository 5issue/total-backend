package com.kurly.wms.infrastructure.entity;

import com.kurly.wms.domain.enums.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "warehouse")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * OMS가 배송지 권역에 따라 창고를 조회할 때 쓴다. 한 창고가 여러 권역을 동시에 담당할 수
     * 있어(예: 김포물류센터가 경기+서울을 같이 커버) PostgreSQL 배열 컬럼(`VARCHAR(20)[]`)으로
     * 매핑한다 — 별도 테이블 없이 이 행 하나에 여러 값을 담는다. 아직 배정되지 않은 창고는
     * 빈 배열이다.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    @Column(name = "regions", columnDefinition = "varchar(20)[]", nullable = false)
    private List<Region> regions = new ArrayList<>();

    @Builder
    private Warehouse(String code, String name, String address, Boolean isActive, List<Region> regions) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.isActive = isActive;
        this.regions = (regions != null) ? regions : new ArrayList<>();
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
