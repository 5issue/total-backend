package com.kurly.wms.domain.enums;

/**
 * 물류 배송 권역. {@link com.kurly.wms.infrastructure.entity.Warehouse}가 담당 권역을
 * 표현하는 데 쓴다 — OMS가 배송지 주소로 판단한 권역에 맞는 창고를 찾을 때 사용.
 *
 * <p>광역자치단체(시/도) 17개를 그대로 쓰지 않고 물류 배정에 의미 있는 단위로 묶었다 — 경기도는
 * 이 회사의 물류센터가 여러 곳 몰려 있어(김포/평택/안산) 동/서로 더 쪼갰고, 나머지 지역은 배송
 * 권역 단위로 크게 묶었다.
 */
public enum Region {
    SEOUL_METRO,
    GYEONGGI_EAST,
    GYEONGGI_WEST,
    CHUNGCHEONG,
    GANGWON,
    YEONGNAM,
    HONAM,
    JEJU
}
