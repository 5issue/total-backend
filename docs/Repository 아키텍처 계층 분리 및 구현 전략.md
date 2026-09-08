## 1. 결정 요약

- **기본 구조**: **2개 파일 구조**(Domain 인터페이스 + Infrastructure Spring Data JPA 인터페이스 다중 상속) 채택
- **확장 전략**: YAGNI(You Aren't Gonna Need It) 원칙에 따라, 단순 CRUD 시점에는 `Impl` 구현체를 생성하지 않고, **복잡한 동적 쿼리(Querydsl) 또는 벌크 연산(JdbcTemplate) 등 기술적 요구사항이 발생하는 시점에만 `CustomRepository` 구조로 확장**

## 2. 채택 구조 상세

### 2.1. 기본 구조 (단순 CRUD 환경)

도메인 계층의 순수성을 유지하면서 불필요한 위임(Delegation) 클래스를 제거하기 위해 Spring Data JPA의 인터페이스 다중 상속 매핑 방식을 적용합니다.

```
src/
├── domain/
│   └── order/
│       └── OrderRepository.java           (순수 도메인 인터페이스)
└── infrastructure/
    └── persistence/
        └── OrderJpaRepository.java        (Spring Data JPA 인터페이스)
```

- **Domain 계층 (`OrderRepository.java`)**Java
    - 순수 비즈니스 명세(Contract) 정의
    - Spring Data JPA, Hibernate 등 특정 프레임워크 종속성 배제

    ```
    public interface OrderRepository{
        Order save(Order order);
        Optional<Order> findById(Long id);
    }
    ```

- **Infrastructure 계층 (`OrderJpaRepository.java`)**Java
    - `JpaRepository`와 도메인 인터페이스 `OrderRepository`를 동시 상속
    - Spring Data JPA 프록시가 구현체를 런타임에 자동 생성하므로 별도 구현 클래스 불필요

    ```
    public interface OrderJpaRepository extends JpaRepository<Order, Long>, OrderRepository{
        // 도메인 인터페이스의 시그니처와 일치하는 메서드는 프록시가 자동 위임 처리
    }
    ```


### 2.2. 확장 구조 (복잡 쿼리/특수 기술 요구 발생 시)

Querydsl, 대용량 벌크 연산(`JdbcTemplate`) 등 세부 구현 코드가 필요한 경우에 한해 **인프라 계층 내부에서만** 확장 클래스를 추가합니다.

```
src/
├── domain/
│   └── order/
│       └── OrderRepository.java           (변경 없음)
└── infrastructure/
    └── persistence/
        ├── OrderJpaRepository.java        (확장 인터페이스 추가 상속)
        ├── OrderCustomRepository.java     (특수 구현 명세 인터페이스)
        └── OrderCustomRepositoryImpl.java (Querydsl / JdbcTemplate 구현체)
```

- **구현 인터페이스 결합 방식 (`OrderJpaRepository.java`)**Java

    ```
    public interface OrderJpaRepository extends
            JpaRepository<Order, Long>,
            OrderRepository,
            OrderCustomRepository{
    }
    ```


## 3. 결정 근거

### 3.1. DIP(의존성 역전 원칙) 준수를 통한 도메인 격리

- Domain 계층은 기술 세부사항(Spring Data JPA, DB 방언 등)을 알지 못하며, 오직 `domain/OrderRepository` 추상화에만 의존합니다.
- 기술 스택 변경 또는 데이터 접근 메커니즘 변경 시 비즈니스 로직 수정 없이 인프라 계층만 교체 가능합니다.

### 3.2. 단순 위임 클래스로 인한 생산성 저하 방지

- Domain Entity와 JPA Entity 간 별도 Mapper를 두지 않는 구조에서 3개 파일 구조(Domain Interface + JpaRepository + Impl)를 전면 도입할 경우:
    - 단순 위임(`orderJpaRepository.save(entity)`)만 수행하는 보일러플레이트 코드가 양산됩니다.
    - 메서드 시그니처 1개 변경 시 3개 파일(Domain Interface, JpaRepository, Impl) 및 단위 테스트 코드를 동시 수정해야 하므로 개발 비용이 3배로 증가합니다.
    - 2개 파일 구조를 통해 불필요한 레이어 오버헤드를 제거합니다.

### 3.3. YAGNI(You Aren't Gonna Need It) 원칙 적용

- 5가지 특수 시나리오(Querydsl 동적 쿼리, Entity-Domain 분리 매핑, Redis+DB 이중 쓰기, JdbcTemplate Bulk Insert, 분산 락 Fallback 제어) 중 현재 프로젝트 스펙에서 즉시 필요한 항목 부재.
- 특수 케이스 발생 시 Spring Data의 `CustomRepository` 패턴으로 인프라 계층 내부에서 유연하게 흡수할 수 있으므로, 초기 단계부터 구조적 복잡도를 높일 이유가 없습니다.

## 4. 팀 합의 내역

- **참여자**: 백엔드 파트 전원 (손하영, 신지훈, 김재우)
- **일시**: 2026. 08. 29.
- **결론**:
    - 기본 개발은 2개 파일 구조(Domain Interface + JpaRepository 다중 상속)로 표준화하여 착수.
    - Querydsl 도입 등 구현체 작성이 불가피한 시점에만 팀 공유 후 확장 구조(`CustomRepositoryImpl`)를 점진 적용.