package com.alumni.showcase.service;

import com.alumni.common.security.AuthPrincipal;
import com.alumni.showcase.common.BizException;
import com.alumni.showcase.common.ErrorCode;
import com.alumni.showcase.dto.HeroDtos.CreateRequest;
import com.alumni.showcase.dto.HeroDtos.Item;
import com.alumni.showcase.dto.HeroDtos.UpdateRequest;
import com.alumni.showcase.entity.HeroSlide;
import com.alumni.showcase.repository.HeroSlideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 首页轮播业务实现（Service 层）。
 *
 * <p>职责：角色校验（school_admin / system_admin 维护）、status 白名单、
 * 展示顺序规整；公开列表仅出 published（SQL 口径保证，红线同 /screen/feed）。
 * 不含 SQL 与 HTTP 细节。</p>
 */
@Service
public class HeroSlideService {

    /** 轮播维护限定角色 */
    private static final Set<String> MANAGER_ROLES = Set.of("school_admin", "system_admin");

    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_WITHDRAWN = "withdrawn";
    /** 展示位置白名单：hero 首屏轮播 / campus 美丽校园 */
    private static final Set<String> PLACEMENTS = Set.of("hero", "campus");

    private final HeroSlideRepository heroSlideRepository;

    public HeroSlideService(HeroSlideRepository heroSlideRepository) {
        this.heroSlideRepository = heroSlideRepository;
    }

    /* ==================== 公开端点 ==================== */

    /** 公开列表：仅 published，按展示顺序（门户首屏轮播数据源）；placement 限定展示位置 */
    @Transactional(readOnly = true)
    public List<Item> publicList(String placement) {
        if (!PLACEMENTS.contains(placement)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "placement 仅允许 hero/campus");
        }
        return heroSlideRepository.findByStatusAndPlacementOrderByDisplayOrderAscIdAsc(STATUS_PUBLISHED, placement)
                .stream().map(HeroSlideService::toItem).toList();
    }

    /* ==================== 管理端点（school_admin / system_admin） ==================== */

    /** 管理列表：全状态（管理端配置页数据源） */
    @Transactional(readOnly = true)
    public List<Item> adminList(AuthPrincipal operator) {
        requireManager(operator);
        return heroSlideRepository.findAllByOrderByDisplayOrderAscIdAsc()
                .stream().map(HeroSlideService::toItem).toList();
    }

    /** 新增轮播位 */
    @Transactional
    public Item create(AuthPrincipal operator, CreateRequest req) {
        requireManager(operator);
        HeroSlide slide = new HeroSlide();
        slide.setTitle(req.title().trim());
        applyOptional(slide, req.subtitle(), req.image(), req.displayOrder(), req.status(), req.placement());
        if (slide.getStatus() == null) {
            slide.setStatus(STATUS_PUBLISHED);
        }
        if (slide.getDisplayOrder() == null) {
            slide.setDisplayOrder(0);
        }
        if (slide.getPlacement() == null) {
            slide.setPlacement("hero");
        }
        return toItem(heroSlideRepository.save(slide));
    }

    /** 修改轮播位（字段均可选，给了才改） */
    @Transactional
    public Item update(AuthPrincipal operator, long id, UpdateRequest req) {
        requireManager(operator);
        HeroSlide slide = heroSlideRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "轮播位不存在"));
        if (req.title() != null) {
            if (req.title().isBlank()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "title 不能为空");
            }
            slide.setTitle(req.title().trim());
        }
        applyOptional(slide, req.subtitle(), req.image(), req.displayOrder(), req.status(), req.placement());
        return toItem(heroSlideRepository.save(slide));
    }

    /** 删除轮播位 */
    @Transactional
    public void delete(AuthPrincipal operator, long id) {
        requireManager(operator);
        if (!heroSlideRepository.existsById(id)) {
            throw new BizException(ErrorCode.NOT_FOUND, "轮播位不存在");
        }
        heroSlideRepository.deleteById(id);
    }

    /* ==================== 内部工具 ==================== */

    private void applyOptional(HeroSlide slide, String subtitle, String image, Integer displayOrder, String status,
                               String placement) {
        if (subtitle != null) {
            slide.setSubtitle(subtitle.isBlank() ? null : subtitle.trim());
        }
        if (image != null) {
            slide.setImage(image.isBlank() ? null : image.trim());
        }
        if (displayOrder != null) {
            slide.setDisplayOrder(displayOrder);
        }
        if (status != null) {
            if (!STATUS_PUBLISHED.equals(status) && !STATUS_WITHDRAWN.equals(status)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "status 仅允许 published/withdrawn");
            }
            slide.setStatus(status);
        }
        if (placement != null) {
            if (!PLACEMENTS.contains(placement)) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "placement 仅允许 hero/campus");
            }
            slide.setPlacement(placement);
        }
    }

    private void requireManager(AuthPrincipal operator) {
        if (operator == null || !MANAGER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅校级 / 系统管理员可维护首页轮播");
        }
    }

    private static Item toItem(HeroSlide slide) {
        return new Item(slide.getId(), slide.getTitle(), slide.getSubtitle(), slide.getImage(),
                slide.getDisplayOrder(), slide.getStatus(), slide.getPlacement(),
                slide.getCreatedAt(), slide.getUpdatedAt());
    }
}
