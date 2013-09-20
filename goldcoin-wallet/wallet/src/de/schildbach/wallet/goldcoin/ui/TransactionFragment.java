/*
 * Copyright 2011-2013 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.goldcoin.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.google.goldcoin.core.Address;
import com.google.goldcoin.core.ScriptException;
import com.google.goldcoin.core.Transaction;
import com.google.goldcoin.core.TransactionConfidence.ConfidenceType;
import com.google.goldcoin.core.Wallet;

import de.schildbach.wallet.goldcoin.AddressBookProvider;
import de.schildbach.wallet.goldcoin.Constants;
import de.schildbach.wallet.goldcoin.WalletApplication;
import de.schildbach.wallet.goldcoin.util.Base43;
import de.schildbach.wallet.goldcoin.util.BitmapFragment;
import de.schildbach.wallet.goldcoin.util.WalletUtils;
import de.schildbach.wallet.goldcoin.R;

/**
 * @author Andreas Schildbach
 */

public final class TransactionFragment extends SherlockFragment
{
	public static final String FRAGMENT_TAG = TransactionFragment.class.getName();

	private static final int SHOW_QR_THRESHOLD_BYTES = 2500;

	private AbstractWalletActivity activity;

    @SuppressWarnings("deprecation")
	private android.text.ClipboardManager clipboardManager;

	private DateFormat dateFormat;
	private DateFormat timeFormat;

	@Override
    @SuppressWarnings("deprecation")
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;

		dateFormat = android.text.format.DateFormat.getDateFormat(activity);
		timeFormat = android.text.format.DateFormat.getTimeFormat(activity);
		clipboardManager = (android.text.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.transaction_fragment, null);
	}

	public void update(final Transaction tx)
	{
		final Wallet wallet = ((WalletApplication) activity.getApplication()).getWallet();

		final byte[] serializedTx = tx.unsafeLitecoinSerialize();

		Address from = null;
		boolean fromMine = false;
		try
		{
			from = tx.getInputs().get(0).getFromAddress();
			fromMine = wallet.isPubKeyHashMine(from.getHash160());
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		Address to = null;
		boolean toMine = false;
		try
		{
			to = tx.getOutputs().get(0).getScriptPubKey().getToAddress();
			toMine = wallet.isPubKeyHashMine(to.getHash160());
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		final View view = getView();

		final Date time = tx.getUpdateTime();
		view.findViewById(R.id.transaction_fragment_time_row).setVisibility(time != null ? View.VISIBLE : View.GONE);
		if (time != null)
		{
			final TextView viewDate = (TextView) view.findViewById(R.id.transaction_fragment_time);
			viewDate.setText((DateUtils.isToday(time.getTime()) ? getString(R.string.time_today) : dateFormat.format(time)) + ", "
					+ timeFormat.format(time));
		}

		try
		{
			final BigInteger amountSent = tx.getValueSentFromMe(wallet);
			view.findViewById(R.id.transaction_fragment_amount_sent_row).setVisibility(amountSent.signum() != 0 ? View.VISIBLE : View.GONE);
			if (amountSent.signum() != 0)
			{
				final TextView viewAmountSent = (TextView) view.findViewById(R.id.transaction_fragment_amount_sent);
				viewAmountSent.setText(Constants.CURRENCY_MINUS_SIGN + WalletUtils.formatValue(amountSent, Constants.GLD_PRECISION));
			}
		}
		catch (final ScriptException x)
		{
			x.printStackTrace();
		}

		final BigInteger amountReceived = tx.getValueSentToMe(wallet);
		view.findViewById(R.id.transaction_fragment_amount_received_row).setVisibility(amountReceived.signum() != 0 ? View.VISIBLE : View.GONE);
		if (amountReceived.signum() != 0)
		{
			final TextView viewAmountReceived = (TextView) view.findViewById(R.id.transaction_fragment_amount_received);
			viewAmountReceived.setText(Constants.CURRENCY_PLUS_SIGN + WalletUtils.formatValue(amountReceived, Constants.GLD_PRECISION));
		}

		final View viewFromButton = view.findViewById(R.id.transaction_fragment_from_button);
		final TextView viewFromLabel = (TextView) view.findViewById(R.id.transaction_fragment_from_label);
		if (from != null)
		{
			final String label = AddressBookProvider.resolveLabel(activity, from.toString());
			final StringBuilder builder = new StringBuilder();

			if (fromMine)
				builder.append(getString(R.string.transaction_fragment_you)).append(", ");

			if (label != null)
			{
				builder.append(label);
			}
			else
			{
				builder.append(from.toString());
				viewFromLabel.setTypeface(Typeface.MONOSPACE);
			}

			viewFromLabel.setText(builder.toString());

			final String addressStr = from.toString();
			viewFromButton.setOnClickListener(new OnClickListener()
			{
				public void onClick(final View v)
				{
					EditAddressBookEntryFragment.edit(getFragmentManager(), addressStr);
				}
			});
		}
		else
		{
			viewFromLabel.setText(null);
		}

		final View viewToButton = view.findViewById(R.id.transaction_fragment_to_button);
		final TextView viewToLabel = (TextView) view.findViewById(R.id.transaction_fragment_to_label);
		if (to != null)
		{
			final String label = AddressBookProvider.resolveLabel(activity, to.toString());
			final StringBuilder builder = new StringBuilder();

			if (toMine)
				builder.append(getString(R.string.transaction_fragment_you)).append(", ");

			if (label != null)
			{
				builder.append(label);
			}
			else
			{
				builder.append(to.toString());
				viewToLabel.setTypeface(Typeface.MONOSPACE);
			}

			viewToLabel.setText(builder.toString());

			final String addressStr = to.toString();
			viewToButton.setOnClickListener(new OnClickListener()
			{
				public void onClick(final View v)
				{
					EditAddressBookEntryFragment.edit(getFragmentManager(), addressStr);
				}
			});
		}
		else
		{
			viewToLabel.setText(null);
		}

		final TextView viewStatus = (TextView) view.findViewById(R.id.transaction_fragment_status);
		final ConfidenceType confidenceType = tx.getConfidence().getConfidenceType();
		if (confidenceType == ConfidenceType.DEAD || confidenceType == ConfidenceType.NOT_IN_BEST_CHAIN)
			viewStatus.setText(R.string.transaction_fragment_status_dead);
		else if (confidenceType == ConfidenceType.NOT_SEEN_IN_CHAIN)
			viewStatus.setText(R.string.transaction_fragment_status_pending);
		else if (confidenceType == ConfidenceType.BUILDING)
			viewStatus.setText(R.string.transaction_fragment_status_confirmed);
		else
			viewStatus.setText(R.string.transaction_fragment_status_unknown);

		final TextView viewHash = (TextView) view.findViewById(R.id.transaction_fragment_hash);
		final View viewHashButton = view.findViewById(R.id.transaction_fragment_hash_button);
		final String txHashString = tx.getHash().toString();
		viewHash.setText(txHashString);
		viewHashButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(final View v)
			{
				clipboardManager.setText(txHashString);
				activity.toast(R.string.transaction_fragment_hash_clipboard_msg);
			}
		});

		final TextView viewLength = (TextView) view.findViewById(R.id.transaction_fragment_length);
		viewLength.setText(Integer.toString(serializedTx.length));

		final ImageView viewQr = (ImageView) view.findViewById(R.id.transaction_fragment_qr);
		if (serializedTx.length < SHOW_QR_THRESHOLD_BYTES)
		{
			viewQr.setVisibility(View.VISIBLE);

			try
			{
				// encode transaction URI
				final ByteArrayOutputStream bos = new ByteArrayOutputStream(serializedTx.length);
				final GZIPOutputStream gos = new GZIPOutputStream(bos);
				gos.write(serializedTx);
				gos.close();

				final byte[] gzippedSerializedTx = bos.toByteArray();
				final boolean useCompressioon = gzippedSerializedTx.length < serializedTx.length;

				final StringBuilder txStr = new StringBuilder("gldtx:");
				txStr.append(useCompressioon ? 'Z' : '-');
				txStr.append(Base43.encode(useCompressioon ? gzippedSerializedTx : serializedTx));

				final Bitmap qrCodeBitmap = WalletUtils.getQRCodeBitmap(txStr.toString().toUpperCase(Locale.US), 512);
				viewQr.setImageBitmap(qrCodeBitmap);
				viewQr.setOnClickListener(new OnClickListener()
				{
					public void onClick(final View v)
					{
						BitmapFragment.show(getFragmentManager(), qrCodeBitmap);
					}
				});
			}
			catch (final IOException x)
			{
				throw new RuntimeException(x);
			}
		}
		else
		{
			viewQr.setVisibility(View.GONE);
		}
	}
}