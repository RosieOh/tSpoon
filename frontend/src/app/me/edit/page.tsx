"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useOverlay } from "@/components/overlays/Overlays";
import { BackButton, NavBar } from "@/components/shell/NavBar";
import { BottomCTA } from "@/components/shell/Screen";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { SkeletonBlock } from "@/components/ui/States";
import { isApiError } from "@/lib/api";
import { useSession, useUpdateProfile } from "@/lib/queries/session";
import type { User } from "@/lib/types";

export default function EditProfilePage() {
  const { user } = useSession();
  return (
    <>
      <NavBar left={<BackButton fallback="/me" />} title="내 정보 고치기" alwaysShowTitle />
      {user ? (
        <ProfileForm key={user.updatedAt} user={user} />
      ) : (
        <div className="space-y-4 px-5 pt-4">
          <SkeletonBlock className="h-14 w-full rounded-field" />
          <SkeletonBlock className="h-14 w-full rounded-field" />
        </div>
      )}
    </>
  );
}

function ProfileForm({ user }: { user: User }) {
  const router = useRouter();
  const { toast } = useOverlay();
  const update = useUpdateProfile();
  const [name, setName] = useState(user.name);
  const [phone, setPhone] = useState(user.phone ?? "");
  const [address, setAddress] = useState(user.address ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const changed = name !== user.name || phone !== (user.phone ?? "") || address !== (user.address ?? "");

  const submit = () => {
    setErrors({});
    update.mutate(
      { name: name.trim(), phone: phone.trim() || null, address: address.trim() || null },
      {
        onSuccess: () => {
          toast("내 정보를 저장했어요");
          router.back();
        },
        onError: (e) => {
          if (isApiError(e) && Object.keys(e.errors).length > 0) setErrors(e.errors);
          else toast(isApiError(e) ? e.userMessage : "저장하지 못했어요", "error");
        },
      },
    );
  };

  return (
    <>
      <form
        id="profile-form"
        className="mx-3 mt-2 space-y-5 rounded-section bg-surface p-5"
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <TextField label="이메일" value={user.email} disabled hint="이메일은 바꿀 수 없어요" />
        <TextField label="이름" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" error={errors.name} />
        <TextField
          label="휴대폰 번호"
          type="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          autoComplete="tel"
          placeholder="010-0000-0000"
        />
        <TextField label="주소" value={address} onChange={(e) => setAddress(e.target.value)} autoComplete="street-address" />
      </form>
      <BottomCTA>
        <Button block type="submit" form="profile-form" disabled={!changed || !name.trim()} loading={update.isPending}>
          저장하기
        </Button>
      </BottomCTA>
    </>
  );
}
