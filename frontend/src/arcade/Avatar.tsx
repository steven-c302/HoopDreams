import type { CSSProperties } from "react";
import type { AvatarView } from "../party/contract.gen";

interface AvatarProps {
  avatar: AvatarView;
  size?: string;
  color?: string;
  fire?: boolean;
}

export function Avatar({ avatar, size = "48px", color, fire = false }: AvatarProps) {
  const style: CSSProperties = { width: size, height: size, fontSize: `calc(${size} * 0.55)`, borderColor: color };
  return (
    <span className={`avatar${fire ? " avatar--fire" : ""}`} style={style}>
      {avatar.kind === "photo" ? <img src={avatar.value} alt="" /> : avatar.value}
    </span>
  );
}
