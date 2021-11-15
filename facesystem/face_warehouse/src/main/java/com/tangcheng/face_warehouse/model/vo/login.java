package com.tangcheng.face_warehouse.model.vo;

import lombok.Data;
import lombok.NonNull;

@Data

public class login {

    private Integer username;

    private String password;

    public login() {

    }

    public login(Integer username,String password) {
        this.username = username;
        this.password = password;
    }
}
