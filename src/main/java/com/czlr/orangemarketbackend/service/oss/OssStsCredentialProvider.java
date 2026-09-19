package com.czlr.orangemarketbackend.service.oss;

public interface OssStsCredentialProvider {

    OssStsCredentials assumeRole(String roleSessionName, long durationSeconds);
}
