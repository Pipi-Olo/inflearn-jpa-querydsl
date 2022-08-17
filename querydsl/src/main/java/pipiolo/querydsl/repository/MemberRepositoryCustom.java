package pipiolo.querydsl.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pipiolo.querydsl.dto.MemberSearchCond;
import pipiolo.querydsl.dto.MemberTeamDto;

import java.util.List;

public interface MemberRepositoryCustom {

    List<MemberTeamDto> search(MemberSearchCond condition);
    Page<MemberTeamDto> searchPageSimple(MemberSearchCond condition, Pageable pageable);
    Page<MemberTeamDto> searchPageComplex(MemberSearchCond condition, Pageable pageable);
    Page<MemberTeamDto> searchPageCount(MemberSearchCond condition, Pageable pageable);
    Page<MemberTeamDto> searchPageFinal(MemberSearchCond condition, Pageable pageable);
}
