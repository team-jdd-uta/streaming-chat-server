package com.example.demo.controller;

import com.example.demo.model.DTO.CustomerDTO;
import com.example.demo.model.DTO.UserInfoDTO;
import com.example.demo.model.DTO.WatchHistoryDTO;
import com.example.demo.service.CustomerService;
import com.example.demo.service.FollowsService;
import com.example.demo.service.WatchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserInfoController {

    private final FollowsService followsService;
    private final CustomerService customerService;
    private final WatchHistoryService watchHistoryService;

    public UserInfoController(FollowsService followsService, CustomerService customerService, WatchHistoryService watchHistoryService) {
        this.followsService = followsService;
        this.customerService = customerService;
        this.watchHistoryService = watchHistoryService;
    }

    @PostMapping("/{userId}/follow")
    @Operation(summary = "팔로우 추가 ", description = "누구를 팔로우함.")
    public boolean subscribeUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {

        String myUserId = request.get("user_id");
        String streamerId = request.get("streamerId");

        return followsService.subscribeUser(myUserId, streamerId);
    }

    @GetMapping("/info/{userId}")
    @Operation(summary = "사용자 정보 조회", description = "아이디, 이름, 팔로우, 팔로워, 스트리밍 횟수")
    public UserInfoDTO getUserInfo(@PathVariable String userId) {
        return UserInfoDTO.builder()
                .userId(userId)
                .userName(customerService.getCustomerById(userId))
                .followers(followsService.getFollowedCount(userId))
                .following(followsService.getFollowingCount(userId))
                .streams(-1) //일단 스트림 횟수 0으로 해놓고 나중에 해결하기.
                .build();
    }

   /*
   * 내가 팔로우 하는 사람들 불러오기
   * */
    @GetMapping("/{userId}/Ifollowing/{page}/{size}")
    @Operation(summary = "내가 팔로우 하는 사람들 불러오기", description ="내가팔로우 어쩌구 " )
    public List<CustomerDTO> getMyFollowing(
            @PathVariable String userId, @PathVariable int page, @PathVariable int size) {
        return followsService.getFollowingList(userId, page, size);
    }

    @GetMapping("/{userId}/watch_history/{offset}/{limit}")
    public List<WatchHistoryDTO> getWatchHistory(@PathVariable String userId, @PathVariable int offset, @PathVariable int limit) {
        return watchHistoryService.getRecentWatchHistoriesByUserId(userId, offset, limit);
    }

    @GetMapping("/{userId}/followingI/{page}/{size}")
    public List<CustomerDTO> getFollowingMe(
            @PathVariable String userId, @PathVariable int page, @PathVariable int size) {
        return followsService.getFollowerList(userId, page, size);
    }
}
