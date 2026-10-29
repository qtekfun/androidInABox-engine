package top.niunaijun.blackbox.entity.am;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Parcel;
import android.os.Parcelable;


public class ReceiverData implements Parcelable {
    public Intent intent;
    public ActivityInfo activityInfo;
    public PendingResultData data;
    /** PendingIntent del invitado: Intent original (Parcel.marshall) que solo se abre en el invitado. */
    public byte[] pendingTarget;
    /** Relleno del PendingIntent (extras, ClipData con la respuesta de RemoteInput). */
    public Intent fillIn;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.intent, flags);
        dest.writeParcelable(this.activityInfo, flags);
        dest.writeParcelable(this.data, flags);
        dest.writeByteArray(this.pendingTarget);
        dest.writeParcelable(this.fillIn, flags);
    }

    public void readFromParcel(Parcel source) {
        this.intent = source.readParcelable(Intent.class.getClassLoader());
        this.activityInfo = source.readParcelable(ActivityInfo.class.getClassLoader());
        this.data = source.readParcelable(PendingResultData.class.getClassLoader());
        this.pendingTarget = source.createByteArray();
        this.fillIn = source.readParcelable(Intent.class.getClassLoader());
    }

    public ReceiverData() {
    }

    protected ReceiverData(Parcel in) {
        this.intent = in.readParcelable(Intent.class.getClassLoader());
        this.activityInfo = in.readParcelable(ActivityInfo.class.getClassLoader());
        this.data = in.readParcelable(PendingResultData.class.getClassLoader());
        this.pendingTarget = in.createByteArray();
        this.fillIn = in.readParcelable(Intent.class.getClassLoader());
    }

    public static final Parcelable.Creator<ReceiverData> CREATOR = new Parcelable.Creator<ReceiverData>() {
        @Override
        public ReceiverData createFromParcel(Parcel source) {
            return new ReceiverData(source);
        }

        @Override
        public ReceiverData[] newArray(int size) {
            return new ReceiverData[size];
        }
    };
}
