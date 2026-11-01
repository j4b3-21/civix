package com.civix.consent.domain;

public enum ConsentPurpose {
    PROFILE_PROCESSING("Store and use your profile details to show relevant services, check eligibility and fill applications you start."),
    DOCUMENT_PROCESSING("Store the documents you upload (encrypted) and read them to suggest profile values that you review before they are used."),
    ELIGIBILITY_EVALUATION("Compare your profile with the official eligibility rules of services you look at. The result is guidance; the department decides."),
    AGENT_EXECUTION("Let the CIVIX assistant prepare applications on official portals for you. Every form, upload and the final submission still needs your explicit approval."),
    PORTAL_DOCUMENT_UPLOAD("Allow approved documents to be uploaded to the official portal of an application you started, only after you approve each upload.");

    private final String description;

    ConsentPurpose(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
