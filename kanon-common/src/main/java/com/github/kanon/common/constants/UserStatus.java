package com.github.kanon.common.constants;

import lombok.Getter;
import lombok.Setter;

/**
 * @Author: PengCheng
 * @Description:    账户状态
 * @Date: 2018/7/26
 */
public enum UserStatus {

    COMMON(0),
    BLOCK(1),
    FROZEN(2);

    @Getter
    @Setter
    private Integer status;

    UserStatus(Integer status) {
        this.status = status;
    }
}
