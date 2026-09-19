package com.czlr.orangemarketbackend.service.oss;

import java.net.URI;
import java.security.PublicKey;

public interface OssCallbackPublicKeyProvider {

    PublicKey getPublicKey(URI url);
}
