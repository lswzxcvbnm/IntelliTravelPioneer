package com.travel.intellitravel_pioneer.service.ai;

import java.util.List;

public interface AiImageClient {

    /**
     * Generate images based on text prompt.
     *
     * @param prompt The text description of the desired image
     * @param count  Number of images to generate (e.g. 1-4)
     * @param size   Image resolution (e.g. "1024x1024")
     * @return List of generated image URLs
     */
    List<String> generateImages(String prompt, int count, String size);
}
