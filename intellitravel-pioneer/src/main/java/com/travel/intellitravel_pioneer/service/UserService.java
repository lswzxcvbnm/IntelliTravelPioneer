package com.travel.intellitravel_pioneer.service;
import com.travel.intellitravel_pioneer.vo.user.UserVO;

public interface UserService {
    String register(UserVO userVO, String password);
    String login(String username, String password);
    UserVO getInformation(String username);
    String updateInformation(UserVO userVO);

    /**
     * Add experience points to a user and return updated user info.
     */
    UserVO addExperience(String username, int experience);
}
