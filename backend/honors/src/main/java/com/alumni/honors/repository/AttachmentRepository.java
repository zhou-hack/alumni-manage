package com.alumni.honors.repository;

import com.alumni.honors.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** 查询某业务记录已引用的全部附件（详情组装 / 换绑释放用） */
    List<Attachment> findByBizTypeAndRefId(String bizType, Long refId);
}
