package com.civix.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.civix.approvals.service.ApprovalTokenSigner;

@Configuration(proxyBeanMethods = false)
public class ApprovalConfig {

    /** Fails startup when APPROVAL_SIGNING_SECRET is missing or a placeholder. */
    @Bean
    public ApprovalTokenSigner approvalTokenSigner(CivixProperties props) {
        return new ApprovalTokenSigner(props.approval() == null ? null : props.approval().signingSecret());
    }
}
