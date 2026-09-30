package com.kurly.user.domain.repository;

import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.enums.AuthProvider;

import java.util.Optional;

public interface UserRepository {

    /**
     * Spring Data의 {@code <S extends T> S save(S)}와 같은 형태로 선언한다.
     * 단순히 {@code T save(T)}로 두면 JpaRepository와 함께 상속했을 때 두 선언이 서로를 재정의하지
     * 못해, 이 타입이 아닌 JpaRepository 타입으로 호출하는 순간 모호성 오류가 난다.
     */
    <S extends User> S save(S user);

    Optional<User> findById(Long id);

    /**
     * 기본 배송지 전환을 직렬화하기 위해 회원 행을 잠근다.
     *
     * <p>배송지 행만으로는 잠글 대상이 없다 — 첫 배송지를 만들 때는 기존 행이 없기 때문이다.
     * 회원 행을 기준점으로 삼아 같은 회원의 전환이 겹치지 않게 한다.
     */
    Optional<User> findByIdForUpdate(Long id);

    /** {@code sync-profile}의 멱등 조회. 소셜 식별자로 기존 회원을 찾는다. */
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    /**
     * 이름이 비어 있을 때만 채운다. <b>비었는지 판단과 갱신을 한 문장으로 묶는다.</b>
     *
     * <p>조회해서 비었는지 보고 저장하면, 동시 요청이 모두 "비어 있음"을 본 뒤 각자 저장해
     * 나중 것이 앞의 것을 덮어쓴다. DB가 승자를 정하게 한다.
     *
     * <p><b>준영속 엔티티의 {@code save}로는 대신할 수 없다.</b> 그것은 merge라서 전 컬럼을
     * UPDATE하므로, 읽어온 뒤 다른 트랜잭션이 바꾼 {@code status}·{@code email}까지
     * 낡은 스냅샷으로 되돌려 놓는다. 이 UPDATE는 {@code name}만 건드린다.
     *
     * @return 갱신된 행 수. 0이면 이미 이름이 있거나 없는 회원이다
     */
    int fillNameIfBlank(Long id, String name);
}
