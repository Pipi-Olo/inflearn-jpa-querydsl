package pipiolo.querydsl.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pipiolo.querydsl.dto.MemberSearchCond;
import pipiolo.querydsl.dto.MemberTeamDto;
import pipiolo.querydsl.repository.MemberJpaRepository;
import pipiolo.querydsl.repository.MemberRepository;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/members")
@RestController
public class MemberController {

    private final MemberJpaRepository memberJpaRepository;
    private final MemberRepository memberRepository;

    @GetMapping("/v1")
    public List<MemberTeamDto> searchMemberV1(MemberSearchCond condition) {
        return memberJpaRepository.search(condition);
    }

    @GetMapping("/v2")
    public Page<MemberTeamDto> searchMemberV2(MemberSearchCond condition, Pageable pageable) {
        return memberRepository.searchPageSimple(condition, pageable);
    }

    @GetMapping("/v3")
    public Page<MemberTeamDto> searchMemberV3(MemberSearchCond condition, Pageable pageable) {
        return memberRepository.searchPageComplex(condition, pageable);
    }

    @GetMapping("/v4")
    public Page<MemberTeamDto> searchMemberV4(MemberSearchCond condition, Pageable pageable) {
        return memberRepository.searchPageCount(condition, pageable);
    }

    @GetMapping("/v5")
    public Page<MemberTeamDto> searchMemberV5(MemberSearchCond condition, Pageable pageable) {
        return memberRepository.searchPageFinal(condition, pageable);
    }
}
