package pipiolo.querydsl.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pipiolo.querydsl.dto.MemberSearchCond;
import pipiolo.querydsl.dto.MemberTeamDto;
import pipiolo.querydsl.repository.MemberJpaRepository;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/members")
@RestController
public class MemberController {

    private final MemberJpaRepository memberJpaRepository;

    @GetMapping("/v1")
    public List<MemberTeamDto> searchMemberV1(MemberSearchCond condition) {
        return memberJpaRepository.search(condition);
    }
}
