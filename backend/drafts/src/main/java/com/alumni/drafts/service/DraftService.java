package com.alumni.drafts.service;

import com.alumni.drafts.common.BizException;
import com.alumni.drafts.common.ErrorCode;
import com.alumni.drafts.dto.DraftDtos.SyncRequest;
import com.alumni.drafts.entity.Draft;
import com.alumni.drafts.repository.DraftRepository;
import com.alumni.common.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 草稿业务实现（Service 层，API 规范第十二章）。
 *
 * <p>同步机制：localStorage 为主、服务端兜底；版本号乐观锁（不使用客户端时间戳 LWW——客户端时间不可信）。</p>
 *
 * <p>跨模块契约（共享库直写，不走 HTTP）：
 * archives 模块正式提交成功（submit=true）后直写删除对应 (user_id, biz_type='archive') 草稿行。</p>
 */
@Service
public class DraftService {

    private final DraftRepository draftRepository;

    public DraftService(DraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    /**
     * 同步结果。conflict=true 表示版本冲突（409），data 携带服务端最新草稿与版本号，前端覆盖本地。
     */
    public record SyncResult(boolean conflict, Map<String, Object> data) {
    }

    /**
     * POST /drafts/sync：版本号乐观锁。
     * - 无服务端草稿 → 新建（version=1）
     * - 提交版本 = 服务端版本 → 保存 payload 并 version+1
     * - 提交版本 &lt; 服务端版本 → 409 冲突，返回服务端最新草稿（前端覆盖本地）
     * - 提交版本 &gt; 服务端版本（服务端草稿曾被正式提交清理/重置）→ 采纳客户端版本继续 +1，避免客户端卡死
     */
    @Transactional
    public SyncResult sync(AuthPrincipal operator, SyncRequest req) {
        requireLogin(operator);
        Draft draft = draftRepository.findByUserIdAndBizType(operator.userId(), req.bizType()).orElse(null);

        if (draft == null) {
            draft = new Draft();
            draft.setUserId(operator.userId());
            draft.setBizType(req.bizType());
            draft.setVersion(1);
            draft.setPayload(req.payload());
            draftRepository.saveAndFlush(draft);
            return new SyncResult(false, toData(draft));
        }

        if (req.version() < draft.getVersion()) {
            // 版本冲突：data 携带服务端最新草稿与版本号，前端覆盖本地
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("version", draft.getVersion());
            data.put("payload", draft.getPayload());
            return new SyncResult(true, data);
        }

        draft.setPayload(req.payload());
        draft.setVersion(Math.max(req.version(), draft.getVersion()) + 1);
        draftRepository.save(draft);
        return new SyncResult(false, toData(draft));
    }

    /** GET /drafts/my：我的草稿列表（换设备恢复用），biz_type 可选过滤，updated_at 倒序 */
    @Transactional(readOnly = true)
    public Map<String, Object> my(AuthPrincipal operator, String bizType) {
        requireLogin(operator);
        List<Draft> drafts = (bizType == null)
                ? draftRepository.findByUserIdOrderByUpdatedAtDesc(operator.userId())
                : draftRepository.findByUserIdAndBizType(operator.userId(), bizType).stream().toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", drafts.stream().map(this::toData).toList());
        return data;
    }

    /** DELETE /drafts/{id}：删除草稿（仅本人） */
    @Transactional
    public Map<String, Object> delete(AuthPrincipal operator, long id) {
        requireLogin(operator);
        Draft draft = draftRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "草稿不存在"));
        if (!draft.getUserId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅本人可删除草稿");
        }
        draftRepository.delete(draft);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("deleted", true);
        return data;
    }

    private void requireLogin(AuthPrincipal operator) {
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
    }

    private Map<String, Object> toData(Draft draft) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", draft.getId());
        map.put("biz_type", draft.getBizType());
        map.put("version", draft.getVersion());
        map.put("payload", draft.getPayload());
        map.put("updated_at", draft.getUpdatedAt());
        return map;
    }
}
