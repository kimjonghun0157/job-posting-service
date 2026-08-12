package com.pickbit.jobpostingservice.api.mapper;

import com.pickbit.jobpostingservice.api.dto.JobPostingResponse;
import com.pickbit.jobpostingservice.domain.entity.JobPosting;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobPostingMapper {

    JobPostingResponse toResponse(JobPosting jobPosting);
}