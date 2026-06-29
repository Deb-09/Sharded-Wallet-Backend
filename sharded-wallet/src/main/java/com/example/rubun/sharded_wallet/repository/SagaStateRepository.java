package com.example.rubun.sharded_wallet.repository;

import com.example.rubun.sharded_wallet.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, String> {

    Optional<SagaState> findByIdempotencyKey(String idempotencyKey);

    // Used on app restart to resume or rollback any stuck sagas
    // A saga is stuck if it never reached COMPLETED or ROLLBACK_COMPLETE
    @Query("SELECT s FROM SagaState s WHERE s.status NOT IN " +
            "('COMPLETED', 'ROLLBACK_COMPLETE')")
    List<SagaState> findAllIncompleteSagas();

    List<SagaState> findBySenderWalletId(Long senderWalletId);
}