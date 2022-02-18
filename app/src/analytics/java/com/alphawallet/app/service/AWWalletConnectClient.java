package com.alphawallet.app.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.alphawallet.app.App;
import com.alphawallet.app.R;
import com.alphawallet.app.entity.walletconnect.WalletConnectV2SessionItem;
import com.alphawallet.app.ui.WalletConnectV2Activity;
import com.alphawallet.app.viewmodel.walletconnect.SignMethodDialogViewModel;
import com.alphawallet.app.widget.SignMethodDialog;
import com.walletconnect.walletconnectv2.client.WalletConnect;
import com.walletconnect.walletconnectv2.client.WalletConnectClient;
import com.walletconnect.walletconnectv2.core.exceptions.WalletConnectException;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import timber.log.Timber;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class AWWalletConnectClient implements WalletConnectClient.WalletDelegate
{
    public static WalletConnect.Model.SessionProposal sessionProposal;

    public static SignMethodDialogViewModel viewModel;
    private Context context;

    public AWWalletConnectClient(Context context)
    {
        this.context = context;
    }

    @Override
    public void onSessionDelete(@NonNull WalletConnect.Model.DeletedSession deletedSession)
    {
    }

    @Override
    public void onSessionNotification(@NonNull WalletConnect.Model.SessionNotification sessionNotification)
    {

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onSessionProposal(@NonNull WalletConnect.Model.SessionProposal sessionProposal)
    {
        AWWalletConnectClient.sessionProposal = sessionProposal;
        Intent intent = new Intent(context, WalletConnectV2Activity.class);
        intent.putExtra("session", WalletConnectV2SessionItem.from(sessionProposal));
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onSessionRequest(@NonNull WalletConnect.Model.SessionRequest sessionRequest)
    {
        String method = sessionRequest.getRequest().getMethod();

        Timber.tag("seaborn").d(sessionRequest.getRequest().getParams());

        WalletConnect.Model.SettledSession settledSession = getSession(sessionRequest.getTopic());
        if ("personal_sign".equals(method))
        {
            showSignDialog(sessionRequest, settledSession);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void showSignDialog(WalletConnect.Model.SessionRequest sessionRequest, WalletConnect.Model.SettledSession settledSession)
    {
        Activity topActivity = App.getInstance().getTopActivity();
        topActivity.runOnUiThread(() ->
        {
            SignMethodDialog signMethodDialog = new SignMethodDialog(topActivity, settledSession, sessionRequest);
            signMethodDialog.show();
        });
    }

    private WalletConnect.Model.SettledSession getSession(String topic)
    {
        List<WalletConnect.Model.SettledSession> listOfSettledSessions = WalletConnectClient.INSTANCE.getListOfSettledSessions();
        for (WalletConnect.Model.SettledSession session : listOfSettledSessions)
        {
            if (session.getTopic().equals(topic))
            {
                return session;
            }
        }
        return null;
    }

    public void pair(String url)
    {
        WalletConnect.Params.Pair pair = new WalletConnect.Params.Pair(url);
        try
        {
            WalletConnectClient.INSTANCE.pair(pair, new WalletConnect.Listeners.Pairing()
            {
                @Override
                public void onSuccess(@NonNull WalletConnect.Model.SettledPairing settledPairing)
                {
                    Timber.i("onSuccess");
                }

                @Override
                public void onError(@NonNull Throwable throwable)
                {
                    Timber.e(throwable);
                }
            });
        } catch (WalletConnectException e)
        {
            Timber.e(e);
        }
    }

    public void approve(WalletConnect.Model.SessionRequest sessionRequest, String result)
    {
        WalletConnect.Model.JsonRpcResponse jsonRpcResponse = new WalletConnect.Model.JsonRpcResponse.JsonRpcResult(sessionRequest.getRequest().getId(), result);
        WalletConnect.Params.Response response = new WalletConnect.Params.Response(sessionRequest.getTopic(), jsonRpcResponse);
        try
        {
            WalletConnectClient.INSTANCE.respond(response, Timber::e);
        } catch (WalletConnectException e)
        {
            Timber.e(e);
        }
    }

    public void reject(WalletConnect.Model.SessionRequest sessionRequest)
    {
        WalletConnect.Model.JsonRpcResponse jsonRpcResponse = new WalletConnect.Model.JsonRpcResponse.JsonRpcError(sessionRequest.getRequest().getId(), new WalletConnect.Model.JsonRpcResponse.Error(0, "User rejected."));
        WalletConnect.Params.Response response = new WalletConnect.Params.Response(sessionRequest.getTopic(), jsonRpcResponse);
        try
        {
            WalletConnectClient.INSTANCE.respond(response, Timber::e);
        } catch (WalletConnectException e)
        {
            Timber.e(e);
        }
    }

    public void approve(WalletConnect.Model.SessionProposal sessionProposal, List<String> accounts, WalletConnectV2Callback callback)
    {
        WalletConnect.Params.Approve approve = new WalletConnect.Params.Approve(sessionProposal, accounts);
        WalletConnectClient.INSTANCE.approve(approve, new WalletConnect.Listeners.SessionApprove()
        {
            @Override
            public void onSuccess(@NonNull WalletConnect.Model.SettledSession settledSession)
            {
                callback.onSessionProposalApproved();
                showNotification();
            }

            @Override
            public void onError(@NonNull Throwable throwable)
            {

            }
        });
    }

    private void showNotification()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            Intent intent = new Intent(context, WalletConnectV2Service.class);
            context.startForegroundService(intent);
        }
    }

    public void reject(WalletConnect.Model.SessionProposal sessionProposal, WalletConnectV2Callback callback)
    {

        WalletConnectClient.INSTANCE.reject(new WalletConnect.Params.Reject(context.getString(R.string.message_reject_request), sessionProposal.getTopic()), new WalletConnect.Listeners.SessionReject()
        {
            @Override
            public void onSuccess(@NonNull WalletConnect.Model.RejectedSession rejectedSession)
            {
                callback.onSessionProposalRejected();
            }

            @Override
            public void onError(@NonNull Throwable throwable)
            {
            }
        });
    }

    public void disconnect(String sessionId, WalletConnectV2Callback callback)
    {
        try
        {
            WalletConnectClient.INSTANCE.disconnect(new WalletConnect.Params.Disconnect(sessionId, "User disconnect the session."), new WalletConnect.Listeners.SessionDelete()
            {
                @Override
                public void onSuccess(@NonNull WalletConnect.Model.DeletedSession deletedSession)
                {
                    callback.onSessionDisconnected();
                }

                @Override
                public void onError(@NonNull Throwable throwable)
                {
                    Timber.e(throwable);
                }
            });
        } catch (WalletConnectException e)
        {
            Timber.e(e);
        }
    }

    public interface WalletConnectV2Callback {
        void onSessionProposalApproved();
        void onSessionProposalRejected();
        void onSessionDisconnected();
    }
}
