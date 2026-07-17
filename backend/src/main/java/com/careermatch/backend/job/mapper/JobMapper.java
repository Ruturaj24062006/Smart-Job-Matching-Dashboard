package com.careermatch.backend.job.mapper;

import com.careermatch.backend.job.dto.JobRequest;
import com.careermatch.backend.job.dto.JobResponse;
import com.careermatch.backend.job.entity.Job;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface JobMapper {
    JobMapper INSTANCE = Mappers.getMapper(JobMapper.class);

    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "jobType", expression = "java(job.getJobType().name())")
    @Mapping(target = "status", expression = "java(job.getStatus().name())")
    JobResponse toResponse(Job job);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "recruiter", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "embedding", ignore = true)
    @Mapping(target = "fts", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Job toEntity(JobRequest request);
}
