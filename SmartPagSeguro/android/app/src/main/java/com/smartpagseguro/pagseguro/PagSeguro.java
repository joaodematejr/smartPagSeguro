package com.smartpagseguro.pagseguro;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPag;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagActivationData;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagAppIdentification;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagCustomPrinterLayout;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagEventData;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagEventListener;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagInitializationResult;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagNFCResult;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagNearFieldCardData;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagPaymentData;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagPrintResult;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagPrinterData;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagPrinterListener;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagTransactionResult;
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagVoidData;
import br.com.uol.pagseguro.plugpagservice.wrapper.data.request.PlugPagBeepData;
import br.com.uol.pagseguro.plugpagservice.wrapper.data.request.PlugPagLedData;


public class PagSeguro extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private PlugPag plugPag;

    private int countPassword = 0;
    private String getPassword = null;

    private String messageCard = null;

    PagSeguro(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    private PackageInfo getPackageInfo() throws Exception {
        return getReactApplicationContext().getPackageManager().getPackageInfo(getReactApplicationContext().getPackageName(), 0);
    }

    @NonNull
    @Override
    public String getName() {
        return "PagSeguro";
    }

    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("PAYMENT_DEBITO", PlugPag.TYPE_DEBITO);
        constants.put("PAYMENT_CREDITO", PlugPag.TYPE_CREDITO);
        constants.put("PAYMENT_PIX", PlugPag.TYPE_PIX);
        constants.put("INSTALLMENT_TYPE_A_VISTA", PlugPag.INSTALLMENT_TYPE_A_VISTA);

        constants.put("RET_OK", PlugPag.RET_OK);

        String appVersion;

        try {
            appVersion = getPackageInfo().versionName;
        } catch (Exception e) {
            appVersion = "unkown";
        }
        constants.put("appVersion", appVersion);

        return constants;
    }

    // CRIA A IDENTIFICAÇÃO DO APLICATIVO
    @ReactMethod
    public void setAppIdendification() {
        new PlugPagAppIdentification(reactContext);
        plugPag = new PlugPag(reactContext);
    }

    // PEGAR A SERIAL DA POS
    @ReactMethod
    public void getSerialNumber(Promise promise) throws NoSuchFieldException, IllegalAccessException {
        String deviceSerial = (String) Build.class.getField("SERIAL").get(null);
        promise.resolve(deviceSerial);
    }

    //ATIVAR O TERMINAL E FAZ O PAGAMENTO
    @ReactMethod
    public void initializeAndActivatePinpad(String activationCode, Promise promise) {
        final PlugPagActivationData activationData = new PlugPagActivationData(activationCode);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<PlugPagInitializationResult> callable = new Callable<PlugPagInitializationResult>() {
            @Override
            public PlugPagInitializationResult call() throws Exception {
                return plugPag.initializeAndActivatePinpad(activationData);
            }
        };
        Future<PlugPagInitializationResult> future = executor.submit(callable);
        executor.shutdown();
        try {
            PlugPagInitializationResult initResult = future.get();
            final WritableMap map = Arguments.createMap();
            map.putInt("retCode", initResult.getResult());
            promise.resolve(map);
        } catch (ExecutionException e) {
            Log.d("PlugPag", e.getMessage());
            promise.reject("error1", e.getMessage());
        } catch (InterruptedException e) {
            Log.d("PlugPag", e.getMessage());
            promise.reject("error2", e.getMessage());
        }
    }

    //REALIZAR O PAGAMENTO
    @ReactMethod
    public void doPayment(String jsonStr, final Promise promise) {
        final PlugPagPaymentData paymentData = JsonParseUtils.getPlugPagPaymentDataFromJson(jsonStr);
        if (paymentData.getType() == PlugPag.TYPE_PIX) {

            plugPag.setEventListener(new PlugPagEventListener() {
                @Override
                public void onEvent(@NonNull PlugPagEventData plugPagEventData) {
                    messageCard = plugPagEventData.getCustomMessage();
                    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                }
            });

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Runnable runnableTask = new Runnable() {

                @Override
                public void run() {
                    PlugPagTransactionResult transactionResult = plugPag.doPayment(paymentData);
                    final WritableMap map = Arguments.createMap();
                    map.putInt("retCode", transactionResult.getResult());
                    map.putString("transactionCode", transactionResult.getTransactionCode());
                    map.putString("transactionId", transactionResult.getTransactionId());
                    map.putString("message", transactionResult.getMessage());
                    map.putString("errorCode", transactionResult.getErrorCode());
                    promise.resolve(map);
                }
            };
            executor.execute(runnableTask);
            executor.shutdown();

        } else {
            plugPag.setEventListener(new PlugPagEventListener() {
                @Override
                public void onEvent(@NonNull PlugPagEventData plugPagEventData) {
                    messageCard = plugPagEventData.getCustomMessage();
                    int code = plugPagEventData.getEventCode();
                    if (code == PlugPagEventData.EVENT_CODE_WAITING_CARD) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_PIN_REQUESTED) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_PIN_OK) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_REMOVED_CARD) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_SALE_APPROVED) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_SALE_NOT_APPROVED) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_AUTHORIZING) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_INSERTED_CARD) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_SALE_END) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_WAITING_REMOVE_CARD) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.EVENT_CODE_DEFAULT) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (code == PlugPagEventData.ON_EVENT_ERROR) {
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", messageCard);
                    } else if (plugPagEventData.getEventCode() == PlugPagEventData.EVENT_CODE_DIGIT_PASSWORD || plugPagEventData.getEventCode() == PlugPagEventData.EVENT_CODE_NO_PASSWORD) {
                        if (plugPagEventData.getEventCode() == PlugPagEventData.EVENT_CODE_DIGIT_PASSWORD) {
                            countPassword++;
                        } else if (plugPagEventData.getEventCode() == PlugPagEventData.EVENT_CODE_NO_PASSWORD) {
                            countPassword = 0;
                        }
                        if (countPassword == 0) {
                            getPassword = "Senha:";
                        } else if (countPassword == 1) {
                            getPassword = "Senha: *";
                        } else if (countPassword == 2) {
                            getPassword = "Senha: **";
                        } else if (countPassword == 3) {
                            getPassword = "Senha: ***";
                        } else if (countPassword == 4) {
                            getPassword = "Senha: ****";
                        } else if (countPassword == 5) {
                            getPassword = "Senha: *****";
                        } else if (countPassword == 6 || countPassword > 6) {
                            getPassword = "Senha: ******";
                        }
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("eventPayments", getPassword);
                    }
                }
            });
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Runnable runnableTask = new Runnable() {

                @Override
                public void run() {
                    PlugPagTransactionResult transactionResult = plugPag.doPayment(paymentData);
                    final WritableMap map = Arguments.createMap();
                    map.putInt("retCode", transactionResult.getResult());
                    map.putString("transactionCode", transactionResult.getTransactionCode());
                    map.putString("transactionId", transactionResult.getTransactionId());
                    map.putString("message", transactionResult.getMessage());

                    map.putInt("code", transactionResult.getResult());
                    map.putString("amount", transactionResult.getAmount());
                    map.putString("bin", transactionResult.getBin());
                    map.putString("cardApplication", transactionResult.getCardApplication());
                    map.putString("cardBrand", transactionResult.getCardBrand());
                    map.putString("errorCode", transactionResult.getErrorCode());
                    map.putString("cardHash", transactionResult.getCardHash());
                    map.putString("holder", transactionResult.getHolder());

                    promise.resolve(map);
                }
            };

            executor.execute(runnableTask);
            executor.shutdown();

        }

    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable boolean params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("connectionEvent", params);
    }

    @ReactMethod
    public void connection() {
        ConnectivityManager conn = (ConnectivityManager) reactContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = conn.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null ? activeNetwork.isConnectedOrConnecting() : false;

        sendEvent(reactContext, "connectionEvent", isConnected);
    }

}
