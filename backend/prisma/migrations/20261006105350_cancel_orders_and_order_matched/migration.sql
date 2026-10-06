-- AlterEnum
ALTER TYPE "OrderStatus" ADD VALUE 'CANCELLED';

-- AlterTable
ALTER TABLE "Evidence" ADD COLUMN     "orderMatched" BOOLEAN NOT NULL DEFAULT true;
