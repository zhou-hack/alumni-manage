package com.alumni.showcase.repository;

import com.alumni.showcase.entity.HeroSlide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HeroSlideRepository extends JpaRepository<HeroSlide, Long> {

    /** 公开列表：仅 published，按 display_order 升序、id 升序稳定排序 */
    List<HeroSlide> findByStatusOrderByDisplayOrderAscIdAsc(String status);

    /** 公开列表（按展示位置）：仅 published + 指定 placement，同序 */
    List<HeroSlide> findByStatusAndPlacementOrderByDisplayOrderAscIdAsc(String status, String placement);

    /** 管理列表：全状态同序 */
    List<HeroSlide> findAllByOrderByDisplayOrderAscIdAsc();
}
