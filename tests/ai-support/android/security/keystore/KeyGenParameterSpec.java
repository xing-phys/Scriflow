package android.security.keystore;
public final class KeyGenParameterSpec implements java.security.spec.AlgorithmParameterSpec {
    public final String alias;
    private KeyGenParameterSpec(String alias) { this.alias=alias; }
    public static final class Builder {
        private final String alias;
        public Builder(String alias,int purpose){this.alias=alias;}
        public Builder setBlockModes(String mode){return this;}
        public Builder setEncryptionPaddings(String padding){return this;}
        public Builder setKeySize(int size){return this;}
        public KeyGenParameterSpec build(){return new KeyGenParameterSpec(alias);}
    }
}
