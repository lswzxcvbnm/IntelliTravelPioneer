package com.travel.intellitravel_pioneer.service.serviceImpl;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.service.UserService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.user.UserVO;

import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private static final int BCRYPT_COST = 12;

    private final UserRepository userRepository;

    private final TokenUtil tokenUtil;

    public UserServiceImpl(UserRepository userRepository, TokenUtil tokenUtil) {
        this.userRepository = userRepository;
        this.tokenUtil = tokenUtil;
    }

    @Override
    public String register(UserVO userVO, String password) {
        if (userRepository.findByUsername(userVO.getUsername()) != null) {
            throw IntellitravelException.userAlreadyExists();
        }
        UserPO userPO = userVO.toPO();
        String hashed = BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
        userPO.setPassword(hashed);
        userRepository.save(userPO);
        return "Success";
    }

    @Override
    public String login(String username, String password) {
        UserPO userPO = userRepository.findByUsername(username);
        if (userPO == null) {
            throw IntellitravelException.usernameOrPasswordError();
        }
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), userPO.getPassword());
        if (!result.verified) {
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
        if (userVO.getName() != null) userPO.setName(userVO.getName());
        if (userVO.getGrade() != null) userPO.setGrade(userVO.getGrade());
        if (userVO.getAvatar() != null) userPO.setAvatar(userVO.getAvatar());
        if (userVO.getTelephone() != null) userPO.setTelephone(userVO.getTelephone());
        if (userVO.getEmail() != null) userPO.setEmail(userVO.getEmail());
        if (userVO.getLocation() != null) userPO.setLocation(userVO.getLocation());
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
