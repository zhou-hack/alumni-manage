package com.alumni.app;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;

/**
 * JPA repository bean 命名：com.alumni.{module}.repository.XxxRepository → {module}XxxRepository。
 * 跨模块同名仓储（ArchiveRepository×4 / HonorRepository×3 等）注入均按类型（构造器注入），
 * bean 名仅用于标识，加模块前缀即可消除冲突，无需改动各模块代码。
 */
public class ModuleBeanNameGenerator implements BeanNameGenerator {

    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        String className = definition.getBeanClassName();
        if (className != null && className.startsWith("com.alumni.")) {
            String[] parts = className.split("\\.");
            if (parts.length >= 4) {
                String module = parts[2];
                String simple = parts[parts.length - 1];
                return module + Character.toUpperCase(simple.charAt(0)) + simple.substring(1);
            }
        }
        return className != null ? className : "anonymousRepository";
    }
}
