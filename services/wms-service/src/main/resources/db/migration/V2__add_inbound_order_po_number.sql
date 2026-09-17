-- SCM 발주(PO) 연계를 위해 InboundOrder에 po_number를 추가한다.
-- V1 작성 시점에는 계약(api-spec)에만 있고 엔티티에는 반영이 안 됐던 컬럼이다.
ALTER TABLE inbound_order ADD COLUMN po_number VARCHAR(50) NOT NULL;
