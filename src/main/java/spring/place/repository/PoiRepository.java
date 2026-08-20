package spring.place.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import spring.place.domain.Poi;

public interface PoiRepository extends JpaRepository<Poi, String> {

    List<Poi> findByFloorId(String floorId);
}
