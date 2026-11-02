package org.eink.inkprobe.notebook;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Public connection settings and a transient decrypted credential; never serialize this object. */
final class NotebookAiConfig {
    enum Provider {
        OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-6-luna"),
        ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
        GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash"),
        DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-flash"),
        CUSTOM("Custom (OpenAI compatible)", "", "");

        final String label, baseUrl, model;
        Provider(String label, String baseUrl, String model) { this.label=label; this.baseUrl=baseUrl; this.model=model; }
        @Override public String toString() { return label; }
    }

    final Provider provider;
    final String baseUrl, model, apiKey;
    final NotebookAiModel specs;

    NotebookAiConfig(Provider provider, String baseUrl, String model, String apiKey) {
        this(provider,baseUrl,model,apiKey,null);
    }
    NotebookAiConfig(Provider provider,String baseUrl,String model,String apiKey,NotebookAiModel specs) {
        if (provider == null) throw new IllegalArgumentException("Choose an AI provider");
        this.provider=provider; this.baseUrl=baseUrl.trim(); this.model=model.trim(); this.apiKey=apiKey.trim();
        this.specs=specs!=null&&specs.id.equals(this.model)?specs:this.model.isEmpty()?null:new NotebookAiModel(this.model);
    }

    String endpoint() {
        String base=baseUrl;
        while (base.endsWith("/")) base=base.substring(0,base.length()-1);
        String suffix=provider==Provider.ANTHROPIC ? "/messages" : "/chat/completions";
        return base.endsWith(suffix) ? base : base+suffix;
    }

    String modelsEndpoint() {
        String endpoint=endpoint(),suffix=provider==Provider.ANTHROPIC?"/messages":"/chat/completions";
        return endpoint.substring(0,endpoint.length()-suffix.length())+"/models";
    }
    boolean sameEndpoint(NotebookAiConfig other) { return other!=null&&provider==other.provider&&endpoint().equals(other.endpoint()); }
    void validate() {
        validateConnection();
        if (model.isEmpty() || model.length()>200 || control(model)) throw new IllegalArgumentException("Enter a valid model ID");
    }
    void validateConnection() {
        try {
            URI url=new URI(endpoint());
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost()==null
                    || url.getRawUserInfo()!=null || url.getRawQuery()!=null || url.getRawFragment()!=null
                    || url.getPort()==0 || url.getPort()>65535 || baseUrl.length()>2048)
                throw new IllegalArgumentException("Use an HTTPS API base URL without credentials, query, or fragment");
        } catch (URISyntaxException error) { throw new IllegalArgumentException("Enter a valid HTTPS API base URL"); }
        if (apiKey.length()>4096 || control(apiKey)) throw new IllegalArgumentException("Enter a valid API key");
        if (apiKey.isEmpty() && provider!=Provider.CUSTOM) throw new IllegalArgumentException("Enter your provider's API key");
    }

    private static boolean control(String value) {
        for (int i=0;i<value.length();i++) if (Character.isISOControl(value.charAt(i))) return true;
        return false;
    }

    /** Conversations belong to one destination and model, independently of key rotation. */
    String profile() {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((provider.name()+"\n"+endpoint()+"\n"+model).getBytes(StandardCharsets.UTF_8));
            StringBuilder name=new StringBuilder();
            for (byte b:hash) name.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            return name.toString();
        } catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
}
