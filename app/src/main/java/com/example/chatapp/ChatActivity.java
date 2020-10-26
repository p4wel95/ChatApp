package com.example.chatapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatActivity extends AppCompatActivity {

    KeyStore keyStore;
    private static final String KEY_ALIAS = "chatapp";
    private static final String AndroidKeyStore = "AndroidKeyStore";
    private static final String RSA_MODE =  "RSA/ECB/PKCS1Padding";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final String FIXED_IV = "123456123456";
    private static final String SHARED_PREFENCE_NAME = "shared_prefs";
    private static final String ENCRYPTED_KEY = "symetricKey";
    private static final String AES_MODE_OLD = "AES/ECB/PKCS7Padding";

    private String interlocutorID, interlocutorName, interlocutorImage, interlocutorPublicKey, currentUserID, currentUserPrivateKey, currentUserPublicKey;
    private TextView userName, userLastSeen;
    private CircleImageView userImage;
    private ImageButton sendMessageButton;
    private EditText messageInputText;

    private Toolbar chatToolbar;

    private FirebaseAuth mAuth;
    private DatabaseReference rootRef, interlocutorRef;

    private final List<Messages> messagesList = new ArrayList<>();
    private LinearLayoutManager linearLayoutManager;
    private MessageAdapter messageAdapter;
    private RecyclerView userMessagesList;

    private SharedPreferences sharedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mAuth = FirebaseAuth.getInstance();
        currentUserID = mAuth.getCurrentUser().getUid();
        rootRef = FirebaseDatabase.getInstance().getReference();

        interlocutorID = getIntent().getExtras().get("visit_user_id").toString();
        interlocutorName = getIntent().getExtras().get("visit_user_name").toString();
        interlocutorImage = getIntent().getExtras().get("visit_user_image").toString();
        interlocutorRef = rootRef.child("Users").child(interlocutorID);
        interlocutorRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists())
                {
                    interlocutorPublicKey = dataSnapshot.child("public_key").getValue().toString();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        sharedPreferences = getSharedPreferences(SHARED_PREFENCE_NAME, MODE_PRIVATE);
        String currentUserPrivateKeyEncrypted = sharedPreferences.getString("private_key","");
        //currentUserPrivateKey = sharedPreferences.getString("private_key","");
        currentUserPublicKey = sharedPreferences.getString("public_key","");

        //--------------- Key Store -----------
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

        try {
            currentUserPrivateKey = keyStoreDecrypt(this, currentUserPrivateKeyEncrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("messagesList count 1 : "+messagesList.size());
        initializeControllers();
        System.out.println("messagesList count 2 : "+messagesList.size());

        userName.setText(interlocutorName);
        Picasso.get().load(interlocutorImage).placeholder(R.drawable.profile_image).into(userImage);

        sendMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        rootRef.child("Messages").child(currentUserID).child(interlocutorID).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Messages messages = dataSnapshot.getValue(Messages.class);
                messagesList.add(messages);
                messageAdapter.notifyDataSetChanged();
                userMessagesList.smoothScrollToPosition(userMessagesList.getAdapter().getItemCount());
                System.out.println("messagesList count 3 : "+messagesList.size());
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
        System.out.println("messagesList count 4 : "+messagesList.size());
    }

    private void initializeControllers() {


        chatToolbar = (Toolbar) findViewById(R.id.chat_toolbar);
        setSupportActionBar(chatToolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View actionBarView =  layoutInflater.inflate(R.layout.custom_chat_bar, null);
        actionBar.setCustomView(actionBarView);

        userImage = (CircleImageView) findViewById(R.id.custom_profile_image);
        userName = (TextView) findViewById(R.id.custom_profile_name);
        userLastSeen = (TextView) findViewById(R.id.custom_user_last_seen);
        sendMessageButton = (ImageButton) findViewById(R.id.send_message_btn);
        messageInputText = (EditText) findViewById(R.id.input_message);

        messageAdapter = new MessageAdapter(messagesList, currentUserPrivateKey);
        userMessagesList = (RecyclerView) findViewById(R.id.private_messages_list_of_users);
        linearLayoutManager = new LinearLayoutManager(this);
        userMessagesList.setLayoutManager(linearLayoutManager);
        userMessagesList.setAdapter(messageAdapter);
        userMessagesList.setItemViewCacheSize(100);
    }

    private void DisplayLastSeen()
    {
        rootRef.child("Users").child(currentUserID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.child("userState").hasChild("state"))
                {
                    String date = dataSnapshot.child("userState").child("date").getValue().toString();
                    String time = dataSnapshot.child("userState").child("time").getValue().toString();
                    String state = dataSnapshot.child("userState").child("state").getValue().toString();

                    if (state.equals("online"))
                    {
                        userLastSeen.setText("online");

                    }
                    else if (state.equals("offline"))
                    {
                        userLastSeen.setText("Last Seen : "+date+" "+time);

                    }
                }
                else
                {
                    userLastSeen.setText("offline");

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void sendMessage()
    {
        String messageText = messageInputText.getText().toString();



        if (!TextUtils.isEmpty(messageText))
        {

            System.out.println("public key 1 : " + currentUserPublicKey);
            System.out.println("public key 2 : " + interlocutorPublicKey);
            String interlocutorPublicKeyEncryptedMessageText = encryptRSAToString(messageText,interlocutorPublicKey);
            String currentUserPublicKeyEncryptedMessageText = encryptRSAToString(messageText,currentUserPublicKey);

            String messageSenderRef = "Messages/"+ currentUserID +"/"+ interlocutorID;
            String messageReceiverRef = "Messages/"+ interlocutorID +"/"+ currentUserID;
            DatabaseReference userMessageKeyRef = rootRef.child("Messages").child(currentUserID).child(interlocutorID).push();
            String messagePushID = userMessageKeyRef.getKey();
            System.out.println("messagePushID : "+messagePushID);
            Map messageTextBody1 = new HashMap<>();
            messageTextBody1.put("message",currentUserPublicKeyEncryptedMessageText);
            messageTextBody1.put("type","text");
            messageTextBody1.put("from", currentUserID);

            Map messageTextBody2 = new HashMap<>();
            messageTextBody2.put("message",interlocutorPublicKeyEncryptedMessageText);
            messageTextBody2.put("type","text");
            messageTextBody2.put("from", currentUserID);

            Map messageBodyDetails = new HashMap();
            messageBodyDetails.put(messageSenderRef+"/"+messagePushID, messageTextBody1);
            messageBodyDetails.put(messageReceiverRef+"/"+messagePushID, messageTextBody2);

            rootRef.updateChildren(messageBodyDetails).addOnCompleteListener(new OnCompleteListener() {
                @Override
                public void onComplete(@NonNull Task task) {
                    if (task.isSuccessful())
                    {
                        Toast.makeText(ChatActivity.this, "Message sent", Toast.LENGTH_SHORT).show();
                    }
                    else
                    {
                        Toast.makeText(ChatActivity.this, "error", Toast.LENGTH_SHORT).show();
                    }
                    messageInputText.setText("");
                }
            });
        }
    }

    private String encryptRSAToString(String clearText, String publicKey) {
        String encryptedBase64 = "";
        try {
            KeyFactory keyFac = KeyFactory.getInstance("RSA");
            KeySpec keySpec = new X509EncodedKeySpec(Base64.decode(publicKey.trim().getBytes(), Base64.DEFAULT));
            Key key = keyFac.generatePublic(keySpec);

            // get an RSA cipher object and print the provider
            final Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA-256andMGF1Padding");
            // encrypt the plain text using the public key
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] encryptedBytes = cipher.doFinal(clearText.getBytes("UTF-8"));
            encryptedBase64 = new String(Base64.encode(encryptedBytes, Base64.DEFAULT));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return encryptedBase64.replaceAll("(\\r|\\n)", "");
    }

    private  String  keyStoreDecrypt(Context context, String input) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            Cipher c = null;
            Key secretKey = keyStore.getKey(KEY_ALIAS, null);
            try {
                c = Cipher.getInstance(AES_MODE);
                c.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, FIXED_IV.getBytes()));
                byte[] encryptedBytes = Base64.decode(input, Base64.DEFAULT);
                byte[] decodedBytes = c.doFinal(encryptedBytes);
                return new String(decodedBytes);
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
                System.out.println("6");
                c = Cipher.getInstance(AES_MODE_OLD, "BC");
                System.out.println("7");
                c.init(Cipher.DECRYPT_MODE, getSecretKey(context));
                System.out.println("8");
                System.out.println("input : " + input.replaceAll("(\\r|\\n)", ""));
                byte[] inputBytes = Base64.decode(input, Base64.DEFAULT);
                byte[] decodedBytes = c.doFinal(inputBytes);
                System.out.println("9");
                return new String (decodedBytes);
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
