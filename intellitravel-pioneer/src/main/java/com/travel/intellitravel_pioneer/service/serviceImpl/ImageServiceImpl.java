package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.service.ImageService;
import com.travel.intellitravel_pioneer.util.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageServiceImpl implements ImageService {

    @Autowired
    private OssUtil ossUtil;

    @Override
    public String upload(MultipartFile file) {
        try {
            return ossUtil.upload(file.getOriginalFilename(), file.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
            throw new IntellitravelException("上传失败");
        }
    }
}