package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.service.UserService;
import com.travel.intellitravel_pioneer.vo.user.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.travel.intellitravel_pioneer.util.TokenUtil;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenUtil tokenUtil;

    @Override
    public String register(UserVO userVO, String password) {
        if (userRepository.findByUsername(userVO.getUsername()) != null) {
            throw IntellitravelException.userAlreadyExists();
        }
        UserPO userPO = userVO.toPO();
        userPO.setPassword(password);
        userRepository.save(userPO);
        return "Success";
    }

    @Override
    public String login(String username, String password) {
        UserPO userPO = userRepository.findByUsername(username);
        if (userPO == null || !userPO.getPassword().equals(password)) {
            throw IntellitravelException.usernameOrPasswordError();
        }
        return tokenUtil.getToken(userPO);
    }

    @Override
    public UserVO getInformation(String username) {
        UserPO userPO = userRepository.findByUsername(username);
        if (userPO == null) {
            throw IntellitravelException.userNotFound();
        }
        return userPO.toVO();
    }

    @Override
    public String updateInformation(UserVO userVO) {
        UserPO userPO = userRepository.findByUsername(userVO.getUsername());
        if (userPO == null) {
            throw IntellitravelException.userNotFound();
        }
        userPO.setName(userVO.getName());
        userPO.setGrade(userVO.getGrade());
        userPO.setAvatar(userVO.getAvatar());
        userPO.setTelephone(userVO.getTelephone());
        userPO.setEmail(userVO.getEmail());
        userPO.setLocation(userVO.getLocation());
        userRepository.save(userPO);
        return "Success";
    }

    @Override
    public UserVO addExperience(String username, int experience) {
        if (experience <= 0) {
            throw new IntellitravelException("experience must be positive");
        }

        UserPO userPO = userRepository.findByUsername(username);
        if (userPO == null) {
            throw IntellitravelException.userNotFound();
        }
        UserPO.LevelUpResult levelUpResult = userPO.addExperience(experience);
        userRepository.save(userPO);
        
        UserVO userVO = userPO.toVO();
        if (levelUpResult != null && levelUpResult.isLevelUp()) {
            userVO.setLevelUpInfo(UserVO.LevelUpInfo.fromResult(levelUpResult));
        }
        
        return userVO;
    }
}
