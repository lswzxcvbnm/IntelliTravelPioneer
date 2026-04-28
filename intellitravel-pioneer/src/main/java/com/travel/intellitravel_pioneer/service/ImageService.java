package com.travel.intellitravel_pioneer.service;

import org.springframework.web.multipart.MultipartFile;


public interface ImageService {
    public String upload(MultipartFile file);
}
