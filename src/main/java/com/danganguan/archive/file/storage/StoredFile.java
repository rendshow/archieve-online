package com.danganguan.archive.file.storage;

public record StoredFile(String relativePath, String sha256, long size) {
}
