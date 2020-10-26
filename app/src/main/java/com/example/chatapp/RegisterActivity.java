package com.example.chatapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

public class RegisterActivity extends AppCompatActivity {

    KeyStore keyStore;
    private static final String KEY_ALIAS = "chatapp";
    private static final String AndroidKeyStore = "AndroidKeyStore";
    private static final String RSA_MODE =  "RSA/ECB/PKCS1Padding";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final String FIXED_IV = "123456123456";
    private static final String SHARED_PREFENCE_NAME = "shared_prefs";
    private static final String ENCRYPTED_KEY = "symetricKey";
    private static final String AES_MODE_OLD = "AES/ECB/PKCS7Padding";
    private static final String SALT = "nk435nkj3njj34k";

    private Button createAccountButton, alreadyHaveAccountLink;
    private EditText userEmail, userPassword;
    private FirebaseAuth mAuth;
    private ProgressDialog loadingBar;
    private DatabaseReference rootRef;







    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        rootRef = FirebaseDatabase.getInstance().getReference();
        initializeFields();

        alreadyHaveAccountLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendUserToLoginActivity();
            }
        });

        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewAccount();
            }
        });


    }




    private void createNewAccount() {
        String email = userEmail.getText().toString();
        final String password = userPassword.getText().toString();
        if (TextUtils.isEmpty(email))
        {
            Toast.makeText(this,"Please enter email",Toast.LENGTH_SHORT).show();
        }
        else if (TextUtils.isEmpty(password))
        {
            Toast.makeText(this,"Please enter password",Toast.LENGTH_SHORT).show();
        }
        else
        {
            loadingBar.setTitle("Creating New Account");
            loadingBar.setMessage("Please wait, while we are creating new account for you");
            loadingBar.setCanceledOnTouchOutside(true);
            loadingBar.show();
            mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful())
                    {
                        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                            @Override
                            public void onComplete(@NonNull Task<InstanceIdResult> task) {
                                String deviceToken = task.getResult().getToken();
                                String currentUserID = mAuth.getCurrentUser().getUid();
                                rootRef.child("Users").child(currentUserID).setValue("");
                                rootRef.child("Users").child(currentUserID).child("device_token").setValue(deviceToken);

                                KeyPair kp = getKeyPair();

                                PublicKey publicKey = kp.getPublic();
                                byte[] publicKeyBytes = publicKey.getEncoded();
                                String publicKeyBytesBase64 = new String(Base64.encode(publicKeyBytes, Base64.DEFAULT));

                                PrivateKey privateKey = kp.getPrivate();
                                byte[] privateKeyBytes = privateKey.getEncoded();
                                String privateKeyBytesBase64 = new String(Base64.encode(privateKeyBytes, Base64.DEFAULT));

                                try {
                                    String privateKeyEncoded = aesEncrypt(privateKeyBytesBase64,SALT+password);
                                    rootRef.child("Users").child(currentUserID).child("public_key").setValue(publicKeyBytesBase64);
                                    rootRef.child("Users").child(currentUserID).child("private_key").setValue(privateKeyEncoded);

                                    //--------------- Key Store - set key  -----------
                                    try {
                                        keyStore = KeyStore.getInstance(AndroidKeyStore);
                                        keyStore.load(null);
                                    } catch (KeyStoreException e) {
                                        e.printStackTrace();
                                    } catch (CertificateException e) {
                                        e.printStackTrace();
                                    } catch (NoSuchAlgorithmException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                    {
                                        try
                                        {
                                            KeyGenerator keyGenerator = null;
                                            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore);
                                            keyGenerator.init(
                                                    new KeyGenParameterSpec.Builder(KEY_ALIAS,
                                                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                                                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                                            .setRandomizedEncryptionRequired(false)
                                                            .build());
                                            keyGenerator.generateKey();

                                        } catch (NoSuchAlgorithmException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchProviderException e) {
                                            e.printStackTrace();
                                        } catch (InvalidAlgorithmParameterException e) {
                                            e.printStackTrace();
                                        }

                                    }
                                    else
                                    {
                                        // Generate the RSA key pairs
                                        try {
                                            // Generate a key pair for encryption
                                            Calendar start = Calendar.getInstance();
                                            Calendar end = Calendar.getInstance();
                                            end.add(Calendar.YEAR, 30);
                                            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(RegisterActivity.this)
                                                    .setAlias(KEY_ALIAS)
                                                    .setSubject(new X500Principal("CN=" + KEY_ALIAS))
                                                    .setSerialNumber(BigInteger.TEN)
                                                    .setStartDate(start.getTime())
                                                    .setEndDate(end.getTime())
                                                    .build();
                                            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, AndroidKeyStore);
                                            kpg.initialize(spec);
                                            kpg.generateKeyPair();

                                        } catch (InvalidAlgorithmParameterException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchAlgorithmException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchProviderException e) {
                                            e.printStackTrace();
                                        }

                                        SharedPreferences pref = getSharedPreferences(SHARED_PREFENCE_NAME, MODE_PRIVATE);
                                        String enryptedKeyB64;
                                        byte[] key = new byte[16];
                                        SecureRandom secureRandom = new SecureRandom();
                                        secureRandom.nextBytes(key);
                                        byte[] encryptedKey = new byte[0];
                                        try
                                        {
                                            encryptedKey = rsaEncrypt(key);
                                        }
                                        catch (Exception e)
                                        {
                                            e.printStackTrace();
                                        }
                                        enryptedKeyB64 = Base64.encodeToString(encryptedKey, Base64.DEFAULT);
                                        SharedPreferences.Editor edit = pref.edit();
                                        edit.putString(ENCRYPTED_KEY, enryptedKeyB64);
                                        edit.commit();
                                        System.out.println("prefs aes generated");

                                    }
                                    //------------------------------------------------------------------------------------------------------------

                                    // ---- KeyStore - encode private key with key from keyStore ---------
                                    String privateKeyEncodedWithKeyStore = keyStoreEncrypt(RegisterActivity.this, privateKeyBytesBase64);
                                    //-------------------------------------------------------------------

                                    SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFENCE_NAME, MODE_PRIVATE);
                                    SharedPreferences.Editor editor = sharedPreferences.edit();

                                    editor.putString("private_key",privateKeyEncodedWithKeyStore);
                                    editor.putString("public_key",publicKeyBytesBase64);
                                    editor.apply();


                                } catch (Exception e) {
                                    e.printStackTrace();
                                }


                                sendUserToMainActivity();
                                Toast.makeText(RegisterActivity.this,"Account created successfully",Toast.LENGTH_SHORT).show();
                                loadingBar.dismiss();
                            }
                        });

                    }
                    else
                    {
                        String message = task.getException().toString();
                        Toast.makeText(RegisterActivity.this,"Error : "+message,Toast.LENGTH_LONG).show();
                        loadingBar.dismiss();
                    }
                }
            });
        }

    }

    private void initializeFields() {
        createAccountButton = (Button) findViewById(R.id.register_button);
        userEmail = (EditText) findViewById(R.id.register_email);
        userPassword = (EditText) findViewById(R.id.regiser_password);
        alreadyHaveAccountLink = (Button) findViewById(R.id.already_have_account_link);

        loadingBar = new ProgressDialog(this);
    }

    private void sendUserToLoginActivity() {
        Intent loginIntent = new Intent(RegisterActivity.this,LoginActivity.class);
        startActivity(loginIntent);
    }

    private void sendUserToMainActivity() {
        Intent mainIntent = new Intent(RegisterActivity.this,MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(mainIntent);
        finish();
    }

    private KeyPair getKeyPair() {
        KeyPair kp = null;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            kp = kpg.generateKeyPair();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return kp;
    }

    private String aesEncrypt (String data, String password) throws Exception
    {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = password.getBytes("UTF-8");
        System.out.println("bytes : "+ Arrays.toString(bytes));
        digest.update(bytes, 0, bytes.length);
        byte[] key = digest.digest();
        System.out.println("key : "+Arrays.toString(key));
        SecretKeySpec secretKeySpec = new SecretKeySpec(key,"AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] encVal = cipher.doFinal(data.getBytes());
        System.out.println("encrypted bytes : "+Arrays.toString(encVal));
        String encryptedValue = Base64.encodeToString(encVal,Base64.DEFAULT);
        return encryptedValue;
    }

    private String keyStoreEncrypt(Context context, String input) throws Exception
    {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            Cipher c = null;
            Key secretKey = keyStore.getKey(KEY_ALIAS, null);
            try {
                c = Cipher.getInstance(AES_MODE);
                c.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, FIXED_IV.getBytes()));
                byte[] encodedBytes = c.doFinal(input.getBytes("UTF-8"));
                String encryptedBase64Encoded = Base64.encodeToString(encodedBytes, Base64.DEFAULT);
                return encryptedBase64Encoded;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
        else
        {
            Cipher c = null;
            try {
                c = Cipher.getInstance(AES_MODE_OLD, "BC");
                c.init(Cipher.ENCRYPT_MODE, getSecretKey(context));
                byte[] encodedBytes = c.doFinal(input.getBytes("UTF-8"));
                String encryptedBase64Encoded =  Base64.encodeToString(encodedBytes, Base64.DEFAULT);
                return encryptedBase64Encoded;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (NoSuchProviderException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private Key getSecretKey(Context context) throws Exception{
        SharedPreferences pref = context.getSharedPreferences(SHARED_PREFENCE_NAME, Context.MODE_PRIVATE);
        String enryptedKeyB64 = pref.getString(ENCRYPTED_KEY, null);
        // need to check null, omitted here
        byte[] encryptedKey = Base64.decode(enryptedKeyB64, Base64.DEFAULT);
        byte[] key = rsaDecrypt(encryptedKey);
        return new SecretKeySpec(key, "AES");
    }

    private byte[] rsaEncrypt(byte[] secret) throws Exception{
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
        // Encrypt the text
        Cipher inputCipher = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        inputCipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, inputCipher);
        cipherOutputStream.write(secret);
        cipherOutputStream.close();

        byte[] vals = outputStream.toByteArray();
        return vals;
    }

    private  byte[]  rsaDecrypt(byte[] encrypted) throws Exception {
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(KEY_ALIAS, null);
        Cipher output = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        output.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());
        CipherInputStream cipherInputStream = new CipherInputStream(
                new ByteArrayInputStream(encrypted), output);
        ArrayList<Byte> values = new ArrayList<>();
        int nextByte;
        while ((nextByte = cipherInputStream.read()) != -1) {
            values.add((byte)nextByte);
        }

        byte[] bytes = new byte[values.size()];
        for(int i = 0; i < bytes.length; i++) {
            bytes[i] = values.get(i).byteValue();
        }
        return bytes;
    }



}
