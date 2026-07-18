package com.danganguan.archive.common.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static Result<Void> fail(String message) {
        return new Result<>(-1, message, null);
    }



}
