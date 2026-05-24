package nl.lamia.fixtestdriver.repository;

import nl.lamia.fixtestdriver.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    List<OrderEntity> findByTestid(String testid);
    void deleteByTestid(String testid);
}
